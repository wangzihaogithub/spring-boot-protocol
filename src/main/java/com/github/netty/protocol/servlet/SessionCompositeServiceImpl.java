package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.core.util.ResourceManager;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Composite session service
 *
 * @author wangzihao
 */
public class SessionCompositeServiceImpl implements SessionService {
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final String name = NamespaceUtil.newIdName(getClass());

    private SessionService sessionService;
    private final ServletContext servletContext;

    public SessionCompositeServiceImpl(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public void enableLocalMemorySession() {
        removeSessionService();
        this.sessionService = new SessionLocalMemoryServiceImpl(servletContext);
    }

    public void enableRemoteRpcSession(InetSocketAddress address) {
        removeSessionService();
        this.sessionService = new SessionRemoteRpcServiceImpl(address);
    }

    public void enableRemoteRpcSession(InetSocketAddress address, int rpcClientIoRatio, int rpcClientIoThreads,
                                       boolean enableRpcHeartLog, int rpcClientHeartIntervalMillSecond, int reconnectIntervalMillSeconds) {
        removeSessionService();
        this.sessionService = new SessionRemoteRpcServiceImpl(address,
                rpcClientIoRatio, rpcClientIoThreads,
                enableRpcHeartLog, rpcClientHeartIntervalMillSecond, reconnectIntervalMillSeconds);
    }

    public void enableLocalFileSession(ResourceManager resourceManager) {
        removeSessionService();
        this.sessionService = new SessionLocalFileServiceImpl(resourceManager, servletContext);
    }

    public void removeSessionService() {
        if (sessionService == null) {
            return;
        }
        try {
            if (sessionService instanceof SessionLocalFileServiceImpl) {
                ((SessionLocalFileServiceImpl) sessionService).getSessionInvalidThread().interrupt();
            }
        } catch (Exception e) {
            //
        }
        sessionService = null;
    }

    @Override
    public void saveSession(Session session) {
        try {
            getSessionServiceImpl().saveSession(session);
        } catch (Throwable t) {
            logger.warn("saveSession error={}", t.toString(), t);
        }
    }

    @Override
    public void removeSession(String sessionId) {
        getSessionServiceImpl().removeSession(sessionId);
    }

    @Override
    public void removeSessionBatch(List<String> sessionIdList) {
        getSessionServiceImpl().removeSessionBatch(sessionIdList);
    }

    @Override
    public Session getSession(String sessionId) {
        try {
            // TODO: 10-16/0016 Lack of automatic switching
            return getSessionServiceImpl().getSession(sessionId);
        } catch (Throwable t) {
            logger.warn("getSession error={}", t.toString(), t);
            return null;
        }
    }

    @Override
    public void changeSessionId(String oldSessionId, String newSessionId) {
        getSessionServiceImpl().changeSessionId(oldSessionId, newSessionId);
    }

    @Override
    public int count() {
        return getSessionServiceImpl().count();
    }

    protected SessionService getSessionServiceImpl() {
        if (sessionService == null) {
            synchronized (this) {
                if (sessionService == null) {
                    enableLocalMemorySession();
                }
            }
        }
        return sessionService;
    }

    @Override
    public String toString() {
        return name;
    }

}
