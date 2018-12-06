package com.github.netty.register.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;

import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存会话服务
 * @author acer01
 * 2018/8/19/019
 */
public class SessionLocalMemoryServiceImpl implements SessionService {

    private String name = NamespaceUtil.newIdName(getClass());
    private Map<String,Session> sessionMap;
    private SessionInvalidThread sessionInvalidThread;

    public SessionLocalMemoryServiceImpl() {
        this(new ConcurrentHashMap<>(256));
    }

    public SessionLocalMemoryServiceImpl(Map<String, Session> sessionMap) {
        this.sessionMap = sessionMap;
        //20秒检查一次过期session
        this.sessionInvalidThread = new SessionInvalidThread(20 * 1000);
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

        //减少创建迭代器
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
     * session过期检测线程
     * @return
     */
    public SessionInvalidThread getSessionInvalidThread() {
        return sessionInvalidThread;
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
            logger.info("LocalMemorySession CheckInvalidSessionThread has been started...");
            while(true){
                try {
                    Thread.sleep(sessionLifeCheckInter);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                for(Session session : sessionMap.values()){
                    if(!session.isValid()){
                        String id = session.getId();
                        logger.info("NettyX - Session(ID="+id+") is invalidated by Session Manager");
                        sessionMap.remove(id);
                    }
                }
            }
        }
    }
}
