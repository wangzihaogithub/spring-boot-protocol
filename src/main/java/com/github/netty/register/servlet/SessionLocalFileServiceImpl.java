package com.github.netty.register.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.core.util.ResourceManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地文件会话服务
 * @author acer01
 * 2018/8/19/019
 */
public class SessionLocalFileServiceImpl implements SessionService {

    private String name = NamespaceUtil.newIdName(getClass());
    private LoggerX logger = new LoggerX(getClass());
    private String rootPath = "/session";
    private ResourceManager resourceManager;
    private SessionInvalidThread sessionInvalidThread;

    public SessionLocalFileServiceImpl(ResourceManager resourceManager) {
        this.resourceManager = Objects.requireNonNull(resourceManager);
        //20秒检查一次过期session
        this.sessionInvalidThread = new SessionInvalidThread(20 * 1000);
        this.sessionInvalidThread.start();
    }

    @Override
    public void saveSession(Session session) {
        String fileName = getFileName(session.getId());

        try (FileOutputStream fileOutputStream = resourceManager.writeFile(rootPath,fileName,false);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)){
            objectOutputStream.writeUTF(session.getId());
            objectOutputStream.writeLong(session.getCreationTime());
            objectOutputStream.writeLong(session.getLastAccessedTime());
            objectOutputStream.writeInt(session.getMaxInactiveInterval());
            objectOutputStream.writeInt(session.getAccessCount());

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

            objectOutputStream.writeInt(attributeSize);
            if(attributeSize > 0) {
                for (Map.Entry<String,Object> entry : attributeMap.entrySet()){
                    Object value = entry.getValue();
                    if (value instanceof Serializable) {
                        String key = entry.getKey();
                        objectOutputStream.writeUTF(key);
                        objectOutputStream.writeObject(value);
                    }
                }
            }

        } catch (IOException e) {
            logger.warn("saveSession error {0}. case:{1}",session,e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeSession(String sessionId) {
        String fileName = getFileName(sessionId);
        resourceManager.delete(rootPath.concat(File.separator).concat(fileName));
    }

    @Override
    public void removeSessionBatch(List<String> sessionIdList) {
        if(sessionIdList == null || sessionIdList.isEmpty()){
            return;
        }
        String path = rootPath.concat(File.separator);
        if(sessionIdList instanceof RandomAccess){
            int size = sessionIdList.size();
            for(int i=0; i<size; i++){
                String fileName = getFileName(sessionIdList.get(i));
                resourceManager.delete(path.concat(fileName));
            }
        }else {
            for(String sessionId : sessionIdList){
                String fileName = getFileName(sessionId);
                resourceManager.delete(path.concat(fileName));
            }
        }
    }

    @Override
    public Session getSession(String sessionId) {
        String fileName = getFileName(sessionId);
        return getSessionByFileName(fileName);
    }

    protected Session getSessionByFileName(String fileName){
        try (FileInputStream fileInputStream = resourceManager.readFile(rootPath,fileName);
             ObjectInputStream ois = new ObjectInputStream(fileInputStream)){

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
                    Object value = null;
                    try {
                        value = ois.readObject();
                    } catch (ClassNotFoundException e) {
                        logger.warn("getSession readObject error {0}. case:{1}",session,e.toString());
                    }
                    attributeMap.put(key,value);
                }
                session.setAttributeMap(attributeMap);
            }

            return session;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            logger.warn("getSession error name={0}. case:{1}",fileName,e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        String oldFileName = getFileName(oldSessionId);
        String newFileName = getFileName(newSessionId);

        try {
            resourceManager.copyTo(rootPath,oldFileName,rootPath,newFileName,4096);
            removeSession(oldSessionId);
        }catch (FileNotFoundException e){
            //
        }catch (IOException e) {
            logger.warn("changeSessionId error oldId={0},newId={1}. case:{2}",oldSessionId,newSessionId,e.toString());
        }
    }

    @Override
    public int count() {
        return resourceManager.countFile(rootPath);
    }

    /**
     * 获取文件名称
     * @param sessionId
     * @return
     */
    public String getFileName(String sessionId) {
        return "s.".concat(sessionId);
    }

    /**
     * 获取过期读秒
     * @param session
     * @return
     */
    public long getExpireSecond(Session session){
        long expireSecond = (session.getMaxInactiveInterval() * 1000 + session.getCreationTime() - System.currentTimeMillis()) / 1000;
        return expireSecond;
    }

    /**
     * session过期检测线程
     * @return
     */
    public SessionInvalidThread getSessionInvalidThread() {
        return sessionInvalidThread;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * 超时的Session无效化，定期执行
     */
    class SessionInvalidThread extends Thread {
        private LoggerX logger = LoggerFactoryX.getLogger(getClass());
        private final long sessionLifeCheckInter;

        private SessionInvalidThread(long sessionLifeCheckInter) {
            super("NettyX-" + NamespaceUtil.newIdName(SessionInvalidThread.class));
            this.sessionLifeCheckInter = sessionLifeCheckInter;
        }

        @Override
        public void run() {
            logger.info("LocalFileSession CheckInvalidSessionThread has been started...");
            while(true){
                try {
                    Thread.sleep(sessionLifeCheckInter);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }

                try {
                    Set<String> sessionFileNames = resourceManager.getResourcePaths(rootPath);
                    if(sessionFileNames != null){
                        for(String sessionFileName : sessionFileNames){
                            try {
                                Session session = getSessionByFileName(sessionFileName);
                                if(session != null && !session.isValid()){
                                    String id = session.getId();
                                    logger.info("NettyX - Session(ID="+id+") is invalidated by Session Manager");
                                    removeSession(id);
                                }
                            }catch (Exception e){
                                logger.warn("SessionInvalidCheck removeSession error case:{0}",e);
                            }
                        }
                    }
                }catch (Exception e){
                    logger.warn("SessionInvalidCheck run error case:{0}",e);
                }
            }
        }
    }
}
