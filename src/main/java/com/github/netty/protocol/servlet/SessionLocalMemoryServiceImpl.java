package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local memory session service
 * @author wangzihao
 * 2018/8/19/019
 */
public class SessionLocalMemoryServiceImpl implements SessionService {
    private String name = NamespaceUtil.newIdName(getClass());
    private Map<String,Session> sessionMap;
    private SessionInvalidThread sessionInvalidThread;

    public SessionLocalMemoryServiceImpl() {
        this(new ConcurrentHashMap<>(32));
    }

    public SessionLocalMemoryServiceImpl(Map<String, Session> sessionMap) {
        this.sessionMap = sessionMap;
        //The expired session is checked every 30 seconds
        this.sessionInvalidThread = new SessionInvalidThread(30);
        this.sessionInvalidThread.start();
    }

    @Override
    public void saveSession(Session session) {
        if(session == null){
            return;
        }
        sessionMap.put(session.getId(),session);
    }

    @Override
    public void removeSession(String sessionId) {
        sessionMap.remove(sessionId);
    }

    @Override
    public void removeSessionBatch(List<String> sessionIdList) {
        if(sessionIdList == null || sessionIdList.isEmpty()){
            return;
        }

        //Reduce the creation of iterators
        if(sessionIdList instanceof RandomAccess){
            int size = sessionIdList.size();
            for(int i=0; i<size; i++){
                String id = sessionIdList.get(i);
                sessionMap.remove(id);
            }
        }else {
            for(String id : sessionIdList){
                sessionMap.remove(id);
            }
        }
    }

    @Override
    public Session getSession(String sessionId) {
        Session session = sessionMap.get(sessionId);
        if(session != null && session.isValid()){
            return session;
        }
        sessionMap.remove(sessionId);
        return null;
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        Session session = sessionMap.remove(oldSessionId);
        if(session != null && session.isValid()){
            sessionMap.put(newSessionId,session);
        }
    }

    @Override
    public int count() {
        return sessionMap.size();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Session expiration detects threads
     * @return SessionInvalidThread
     */
    public SessionInvalidThread getSessionInvalidThread() {
        return sessionInvalidThread;
    }

    /**
     * Sessions with a timeout are invalidated and executed periodically
     */
    class SessionInvalidThread extends Thread {
        private LoggerX logger = LoggerFactoryX.getLogger(getClass());
        //Unit seconds
        private final int sessionLifeCheckInter;

        private SessionInvalidThread(int sessionLifeCheckInter) {
            super("NettyX-" + NamespaceUtil.newIdName(SessionInvalidThread.class));
            this.sessionLifeCheckInter = sessionLifeCheckInter;
            setPriority(MIN_PRIORITY);
        }

        @Override
        public void run() {
            logger.debug("LocalMemorySession CheckInvalidSessionThread has been started...");
            while(true){
                int maxInactiveInterval = sessionLifeCheckInter;
                Iterator<Session> iterator = sessionMap.values().iterator();
                while (iterator.hasNext()){
                    Session session = iterator.next();
                    if(session.isValid()){
                        maxInactiveInterval = Math.min(maxInactiveInterval,session.getMaxInactiveInterval());
                    }else {
                        String id = session.getId();
                        logger.debug("Session(ID={}) is invalidated by Session Manager",id);
                        iterator.remove();
                    }
                }
                try {
                    int sleepTime = maxInactiveInterval * 1000;
                    if(logger.isDebugEnabled()) {
                        logger.debug("plan next Check {}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis() + sleepTime)));
                    }
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
