package com.github.netty.register.servlet;

import com.github.netty.core.constants.CoreConstants;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.register.rpc.RpcClient;
import com.github.netty.register.rpc.exception.RpcDecodeException;
import com.github.netty.register.rpc.service.RpcDBService;
import com.github.netty.springboot.NettyProperties;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.FastThreadLocal;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 远程会话服务
 * @author acer01
 * 2018/8/19/019
 */
public class SessionRemoteRpcServiceImpl implements SessionService {

    private String name = NamespaceUtil.newIdName(getClass());
    private static final byte[] EMPTY = new byte[0];
    private NettyProperties config;
    private InetSocketAddress address;

    private static final String SESSION_GROUP = "/session";

    private FastThreadLocal<RpcClient> rpcClientThreadLocal = new FastThreadLocal<RpcClient>(){
        @Override
        protected RpcClient initialValue() throws Exception {
            RpcClient rpcClient = new RpcClient("Session",address);
            rpcClient.setIoRatio(config.getRpcClientIoRatio());
            rpcClient.setIoThreadCount(config.getRpcClientIoThreads());
            rpcClient.setSocketChannelCount(config.getRpcClientChannels());
            rpcClient.run();
            if(config.isEnablesRpcClientAutoReconnect()) {
                rpcClient.enableAutoReconnect(config.getRpcClientHeartIntervalSecond(), TimeUnit.SECONDS,null,config.isEnableRpcHeartLog());
            }
            return rpcClient;
        }
    };

    public SessionRemoteRpcServiceImpl(InetSocketAddress address, NettyProperties config) {
        this.address = address;
        this.config = config;
    }

    @Override
    public void saveSession(Session session) {
        byte[] bytes = encode(session);
        long expireSecond = (session.getMaxInactiveInterval() * 1000 + session.getCreationTime() - System.currentTimeMillis()) / 1000;

        if(CoreConstants.isEnableExecuteHold()) {
            CoreConstants.holdExecute(new Runnable() {
                @Override
                public void run() {
                    if (expireSecond > 0) {
                        getRpcDBService().put4(session.getId(), bytes, (int) expireSecond,SESSION_GROUP);
                    } else {
                        getRpcDBService().remove2(session.getId(),SESSION_GROUP);
                    }
                };
            });
            return;
        }


        if (expireSecond > 0) {
            getRpcDBService().put4(session.getId(), bytes, (int) expireSecond,SESSION_GROUP);
        } else {
            getRpcDBService().remove2(session.getId(),SESSION_GROUP);
        }
    }

    @Override
    public void removeSession(String sessionId) {
        getRpcDBService().remove2(sessionId,SESSION_GROUP);
    }

    @Override
    public void removeSessionBatch(List<String> sessionIdList) {
        getRpcDBService().removeBatch2(sessionIdList,SESSION_GROUP);
    }

    @Override
    public Session getSession(String sessionId) {
        if(CoreConstants.isEnableExecuteHold()) {
            return CoreConstants.holdExecute(new Supplier<Session>() {
                @Override
                public Session get() {
                    byte[] bytes = getRpcDBService().get2(sessionId,SESSION_GROUP);
                    Session session = decode(bytes);
                    return session;
                }
            });
        }

        byte[] bytes = getRpcDBService().get2(sessionId,SESSION_GROUP);
        Session session = decode(bytes);
        return session;
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        getRpcDBService().changeKey3(oldSessionId,newSessionId,SESSION_GROUP);;
    }

    @Override
    public int count() {
        return getRpcDBService().count(SESSION_GROUP);
    }

    /**
     * 解码
     * @param bytes
     * @return
     */
    protected Session decode(byte[] bytes){
        if(bytes == null || bytes.length == 0){
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
            if(attributeSize > 0) {
                Map<String, Object> attributeMap = new ConcurrentHashMap<>(6);
                for(int i = 0; i< attributeSize; i++){
                    String key = ois.readUTF();
                    Object value = ois.readObject();
                    attributeMap.put(key,value);
                }
                session.setAttributeMap(attributeMap);
            }

            return session;
        } catch (Exception e) {
            throw new RpcDecodeException(e.getMessage(),e);
        } finally {
            try {
                if(ois != null) {
                    ois.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if(bfi != null){
                    bfi.close();
                }
            }catch (Exception e){
                e.printStackTrace();
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
     * 编码
     * @param session
     * @return
     */
    protected byte[] encode(Session session){
        if(session == null){
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

            Map<String,Object> attributeMap = session.getAttributeMap();

            int attributeSize = 0;
            if(attributeMap != null) {
                for (Map.Entry<String, Object> entry : attributeMap.entrySet()) {
                    if (entry.getValue() instanceof Serializable) {
                        attributeSize++;
                    }else {
//                        logger.warn("session属性中key={}的value未实现序列化, 已自动跳过",entry.getKey());
                    }
                }
            }

            oout.writeInt(attributeSize);
            if(attributeSize > 0) {
                for (Map.Entry<String,Object> entry : attributeMap.entrySet()){
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
            e.printStackTrace();
        } finally {
            if (oout != null) {
                try {
                    oout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    bout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }

}
