package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.exception.RpcDecodeException;
import com.github.netty.protocol.nrpc.exception.RpcEncodeException;
import com.github.netty.protocol.nrpc.service.RpcDBService;
import io.netty.util.concurrent.FastThreadLocal;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote session service
 *
 * @author wangzihao
 * 2018/8/19/019
 */
public class SessionRemoteRpcServiceImpl implements SessionService {
    private static final String SESSION_GROUP = "/session";
    private static final LoggerX logger = LoggerFactoryX.getLogger(SessionRemoteRpcServiceImpl.class);
    private static final byte[] EMPTY = {};
    private final String name = NamespaceUtil.newIdName(getClass());
    private final InetSocketAddress address;
    private final int ioRatio;
    private final int ioThreadCount;
    private final boolean enableRpcHeartLog;
    private final int rpcClientHeartIntervalMillSecond;
    private final int reconnectIntervalMillSeconds;
    private final FastThreadLocal<RpcClient> rpcClientThreadLocal = new FastThreadLocal<RpcClient>() {
        @Override
        protected RpcClient initialValue() throws Exception {
            RpcClient rpcClient = new RpcClient("Session", address);
            rpcClient.setIoRatio(ioRatio);
            rpcClient.setIoThreadCount(ioThreadCount);
//            rpcClient.setSocketChannelCount(clientChannels);
            rpcClient.setIdleTimeMs(rpcClientHeartIntervalMillSecond);
            rpcClient.setReconnectScheduledIntervalMs(reconnectIntervalMillSeconds);
            rpcClient.setEnableRpcHeartLog(enableRpcHeartLog);
            return rpcClient;
        }
    };

    public SessionRemoteRpcServiceImpl(InetSocketAddress address) {
        this(address, 100, 0, false, 20, 20);
    }

    public SessionRemoteRpcServiceImpl(InetSocketAddress address,
                                       int rpcClientIoRatio, int rpcClientIoThreads,
                                       boolean enableRpcHeartLog, int rpcClientHeartIntervalMillSecond, int reconnectIntervalMillSeconds) {
        this.address = address;
        this.ioRatio = rpcClientIoRatio;
        this.ioThreadCount = rpcClientIoThreads;
        this.enableRpcHeartLog = enableRpcHeartLog;
        this.rpcClientHeartIntervalMillSecond = rpcClientHeartIntervalMillSecond;
        this.reconnectIntervalMillSeconds = reconnectIntervalMillSeconds;
    }

    @Override
    public void saveSession(Session session) {
        byte[] bytes = encode(session);
        long expireSecond = (session.getMaxInactiveInterval() * 1000L + session.getCreationTime() - System.currentTimeMillis()) / 1000;
        if (expireSecond > 0) {
            getRpcDBService().put4(session.getId(), bytes, (int) expireSecond, SESSION_GROUP);
        } else {
            getRpcDBService().remove2(session.getId(), SESSION_GROUP);
        }
    }

    @Override
    public void removeSession(String sessionId) {
        getRpcDBService().remove2(sessionId, SESSION_GROUP);
    }

    @Override
    public void removeSessionBatch(List<String> sessionIdList) {
        getRpcDBService().removeBatch2(sessionIdList, SESSION_GROUP);
    }

    @Override
    public Session getSession(String sessionId) {
        byte[] bytes = getRpcDBService().get2(sessionId, SESSION_GROUP);
        return decode(bytes);
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        getRpcDBService().changeKey3(oldSessionId, newSessionId, SESSION_GROUP);
    }

    @Override
    public int count() {
        return getRpcDBService().count(SESSION_GROUP);
    }

    /**
     * decoding
     *
     * @param bytes
     * @return
     */
    protected Session decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        ObjectInputStream ois = null;
        InputStream bfi = null;
        try {
            bfi = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bfi);

            Session session = new Session();
            session.setId(ois.readUTF());
            session.setCreationTime(ois.readLong());
            session.setLastAccessedTime(ois.readLong());
            session.setMaxInactiveInterval(ois.readInt());
            session.setAccessCount(ois.readInt());

            int attributeSize = ois.readInt();
            if (attributeSize > 0) {
                Map<String, Object> attributeMap = new ConcurrentHashMap<>(6);
                for (int i = 0; i < attributeSize; i++) {
                    String key = ois.readUTF();
                    Object value = ois.readObject();
                    attributeMap.put(key, value);
                }
                session.setAttributeMap(attributeMap);
            }

            return session;
        } catch (Exception e) {
            throw new RpcDecodeException("decode http session error=" + e, e);
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException e) {
                //skip
            }
            try {
                if (bfi != null) {
                    bfi.close();
                }
            } catch (Exception e) {
                //skip
            }
        }
    }

    public RpcClient getRpcClient() {
        return rpcClientThreadLocal.get();
    }

    public RpcDBService getRpcDBService() {
        return getRpcClient().getRpcDBService();
    }

    /**
     * coding
     *
     * @param session
     * @return
     */
    protected byte[] encode(Session session) {
        if (session == null) {
            return EMPTY;
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream(64);
        ObjectOutputStream oout = null;
        try {
            oout = new ObjectOutputStream(bout);
            oout.writeUTF(session.getId());
            oout.writeLong(session.getCreationTime());
            oout.writeLong(session.getLastAccessedTime());
            oout.writeInt(session.getMaxInactiveInterval());
            oout.writeInt(session.getAccessCount());

            Map<String, Object> attributeMap = session.getAttributeMap();

            int attributeSize = 0;
            if (attributeMap != null) {
                for (Map.Entry<String, Object> entry : attributeMap.entrySet()) {
                    if (entry.getValue() instanceof Serializable) {
                        attributeSize++;
                    } else {
                        logger.warn("The value of key={} in the http session property is not serialized and has been skipped automatically", entry.getKey());
                    }
                }
            }

            oout.writeInt(attributeSize);
            if (attributeSize > 0) {
                for (Map.Entry<String, Object> entry : attributeMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Serializable) {
                        String key = entry.getKey();
                        oout.writeUTF(key);
                        oout.writeObject(value);
                    }
                }
            }

            oout.flush();
            return bout.toByteArray();
        } catch (IOException e) {
            throw new RpcEncodeException("encode http session error=" + e, e);
        } finally {
            if (oout != null) {
                try {
                    oout.close();
                } catch (IOException e) {
                    //skip
                }
            } else {
                try {
                    bout.close();
                } catch (IOException e) {
                    //skip
                }
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }

}
