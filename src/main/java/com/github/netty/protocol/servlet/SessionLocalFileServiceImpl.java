package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.core.util.ResourceManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local file session service
 * @author wangzihao
 * 2018/8/19/019
 */
public class SessionLocalFileServiceImpl implements SessionService {
    private String name = NamespaceUtil.newIdName(getClass());
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private String rootPath = "/session";
    private ResourceManager resourceManager;
    private SessionInvalidThread sessionInvalidThread;

    public SessionLocalFileServiceImpl(ResourceManager resourceManager) {
        this.resourceManager = Objects.requireNonNull(resourceManager);
        //The expired session is checked every 20 seconds
        this.sessionInvalidThread = new SessionInvalidThread(20 * 1000);
        this.sessionInvalidThread.start();
    }

    @Override
    public void saveSession(Session session) {
        String fileName = getFileName(session.getId());

        try (FileOutputStream fileOutputStream = resourceManager.newFileOutputStream(rootPath,fileName,false);
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
//                        logger.warn("The value of key={} in the session property is not serialized and has been skipped automatically",entry.getKey());
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
            logger.warn("saveSession error {}. case:{}",session,e.toString());
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
        try (FileInputStream fileInputStream = resourceManager.newFileInputStream(rootPath,fileName);
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
                        logger.warn("getSession readObject error {}. case:{}",session,e.toString());
                    }
                    attributeMap.put(key,value);
                }
                session.setAttributeMap(attributeMap);
            }

            return session;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            logger.warn("getSession error name={}. case:{}",fileName,e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        String oldFileName = getFileName(oldSessionId);
        String newFileName = getFileName(newSessionId);

        try {
            resourceManager.copyFile(rootPath,oldFileName,rootPath,newFileName);
            removeSession(oldSessionId);
        }catch (FileNotFoundException e){
            //
        }catch (IOException e) {
            logger.warn("changeSessionId error oldId={},newId={}. case:{}",oldSessionId,newSessionId,e.toString());
        }
    }

    @Override
    public int count() {
        return resourceManager.countFile(rootPath);
    }

    /**
     * Get file name
     * @param sessionId sessionId
     * @return fileName
     */
    public String getFileName(String sessionId) {
        return sessionId.concat(".s");
    }

    /**
     * Gets an expired read second
     * @param session session
     * @return expireSecond
     */
    public long getExpireSecond(Session session){
        long expireSecond = (session.getMaxInactiveInterval() * 1000 + session.getCreationTime() - System.currentTimeMillis()) / 1000;
        return expireSecond;
    }

    /**
     * Session expiration detects threads
     * @return SessionInvalidThread
     */
    public SessionInvalidThread getSessionInvalidThread() {
        return sessionInvalidThread;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Sessions with a timeout are invalidated and executed periodically
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
