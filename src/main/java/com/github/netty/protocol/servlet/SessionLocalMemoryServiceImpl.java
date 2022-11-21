package com.github.netty.protocol.servlet;

import com.github.netty.core.util.ExpiryLRUMap;
import com.github.netty.core.util.NamespaceUtil;

import java.util.List;
import java.util.RandomAccess;

/**
 * Local memory session service
 *
 * @author wangzihao
 * 2018/8/19/019
 */
public class SessionLocalMemoryServiceImpl implements SessionService {
    private final String name = NamespaceUtil.newIdName(getClass());
    private final ExpiryLRUMap<String, Session> sessionMap = new ExpiryLRUMap<>();
    private final ServletContext servletContext;

    public SessionLocalMemoryServiceImpl(ServletContext servletContext) {
        this.servletContext = servletContext;
        sessionMap.setOnExpiryConsumer(this::onInvalidate);
        sessionMap.setOnRemoveConsumer(this::onInvalidate);
    }

    private void onInvalidate(ExpiryLRUMap.Node<String, Session> node) {
        if (node.isCovered()) {
            return;
        }
        ServletHttpSession httpSession = new ServletHttpSession(node.getData(), servletContext);
        if (httpSession.hasListener()) {
            servletContext.getDefaultExecutorSupplier().get().execute(httpSession::invalidate0);
        } else {
            httpSession.invalidate0();
        }
    }

    @Override
    public void saveSession(Session session) {
        if (session == null) {
            return;
        }
        sessionMap.put(session.getId(), session, session.getMaxInactiveInterval() * 1000);
    }

    @Override
    public void removeSession(String sessionId) {
        sessionMap.remove(sessionId);
    }

    @Override
    public void removeSessionBatch(List<String> sessionIdList) {
        if (sessionIdList == null || sessionIdList.isEmpty()) {
            return;
        }

        //Reduce the creation of iterators
        if (sessionIdList instanceof RandomAccess) {
            int size = sessionIdList.size();
            for (int i = 0; i < size; i++) {
                String id = sessionIdList.get(i);
                sessionMap.remove(id);
            }
        } else {
            for (String id : sessionIdList) {
                sessionMap.remove(id);
            }
        }
    }

    @Override
    public Session getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        Session session = sessionMap.remove(oldSessionId);
        if (session != null) {
            long expireTimestamp = session.getCreationTime() + (session.getMaxInactiveInterval() * 1000L);
            long timeout = expireTimestamp - System.currentTimeMillis();
            if (timeout > 0) {
                sessionMap.put(newSessionId, session, timeout);
            }
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

}
