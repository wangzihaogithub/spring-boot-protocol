package com.github.netty.protocol.servlet.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * WebSocket Server Container. Note: only the server is implemented, but the client is not
 * @author wangzihao
 */
public class WebSocketServerContainer implements WebSocketContainer,ServerContainer {
    private final Map<Endpoint, Set<Session>> endpointSessionMap = new HashMap<>();
    private final Map<Session,Session> sessions = new ConcurrentHashMap<>();
    private final Object endPointSessionMapLock = new Object();

    private long defaultAsyncTimeout = -1;
    private int maxBinaryMessageBufferSize = 8 * 1024;
    private int maxTextMessageBufferSize = 8 * 1024;
    private volatile long defaultMaxSessionIdleTimeout = 0;
    private final ConcurrentMap<String,Set<Session>> authenticatedSessions = new ConcurrentHashMap<>();
    private static final CloseReason AUTHENTICATED_HTTP_SESSION_CLOSED = new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,
                    "This connection was established under an authenticated " +
                            "HTTP session that has ended.");

    public WebSocketServerContainer() {
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return defaultMaxSessionIdleTimeout;
    }


    @Override
    public void setDefaultMaxSessionIdleTimeout(long timeout) {
        this.defaultMaxSessionIdleTimeout = timeout;
    }


    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }


    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max) {
        maxBinaryMessageBufferSize = max;
    }


    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }


    @Override
    public void setDefaultMaxTextMessageBufferSize(int max) {
        maxTextMessageBufferSize = max;
    }


    /**
     * {@inheritDoc}
     *
     * Currently, this implementation does not support any extensions.
     */
    @Override
    public Set<Extension> getInstalledExtensions() {
        return Collections.emptySet();
    }


    /**
     * {@inheritDoc}
     *
     * The default value for this implementation is -1.
     */
    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncTimeout;
    }


    /**
     * {@inheritDoc}
     *
     * The default value for this implementation is -1.
     */
    @Override
    public void setAsyncSendTimeout(long timeout) {
        this.defaultAsyncTimeout = timeout;
    }

    @Override
    public Session connectToServer(Object endpoint, URI path) throws DeploymentException, IOException {
        return null;
    }

    @Override
    public Session connectToServer(Class<?> annotatedEndpointClass, URI path) throws DeploymentException, IOException {
        return null;
    }

    @Override
    public Session connectToServer(Endpoint endpoint, ClientEndpointConfig clientEndpointConfiguration, URI path) throws DeploymentException, IOException {
        return null;
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> endpoint, ClientEndpointConfig clientEndpointConfiguration, URI path) throws DeploymentException, IOException {
        return null;
    }

    @Override
    public void addEndpoint(Class<?> clazz) throws DeploymentException {

    }

    @Override
    public void addEndpoint(ServerEndpointConfig sec) throws DeploymentException {

    }


    protected void registerSession(Endpoint endpoint, Session wsSession) {
        if (!wsSession.isOpen()) {
            // The session was closed during onOpen. No need to supportPipeline it.
            return;
        }
        synchronized (endPointSessionMapLock) {
//            if (endpointSessionMap.size() == 0) {
//                BackgroundProcessManager.getInstance().supportPipeline(this);
//            }
            Set<Session> wsSessions = endpointSessionMap.get(endpoint);
            if (wsSessions == null) {
                wsSessions = new HashSet<>();
                endpointSessionMap.put(endpoint, wsSessions);
            }
            wsSessions.add(wsSession);
        }
        sessions.put(wsSession, wsSession);
    }


    protected void unregisterSession(Endpoint endpoint, Session wsSession) {
        synchronized (endPointSessionMapLock) {
            Set<Session> wsSessions = endpointSessionMap.get(endpoint);
            if (wsSessions != null) {
                wsSessions.remove(wsSession);
                if (wsSessions.size() == 0) {
                    endpointSessionMap.remove(endpoint);
                }
            }
        }
        sessions.remove(wsSession);
    }

    private void registerAuthenticatedSession(Session wsSession,
                                              String httpSessionId) {
        Set<Session> wsSessions = authenticatedSessions.get(httpSessionId);
        if (wsSessions == null) {
            wsSessions = Collections.newSetFromMap(
                    new ConcurrentHashMap<Session,Boolean>());
            authenticatedSessions.putIfAbsent(httpSessionId, wsSessions);
            wsSessions = authenticatedSessions.get(httpSessionId);
        }
        wsSessions.add(wsSession);
    }

    private void unregisterAuthenticatedSession(Session wsSession,
                                                String httpSessionId) {
        Set<Session> wsSessions = authenticatedSessions.get(httpSessionId);
        // wsSessions will be null if the HTTP session has ended
        if (wsSessions != null) {
            wsSessions.remove(wsSession);
        }
    }

    public void closeAuthenticatedSession(String httpSessionId) {
        Set<Session> wsSessions = authenticatedSessions.remove(httpSessionId);

        if (wsSessions != null && !wsSessions.isEmpty()) {
            for (Session wsSession : wsSessions) {
                try {
                    wsSession.close(AUTHENTICATED_HTTP_SESSION_CLOSED);
                } catch (IOException e) {
                    // Any IOExceptions during close will have been caught and the
                    // onError method called.
                }
            }
        }
    }


    public Set<Session> getOpenSessions(Endpoint endpoint) {
        HashSet<Session> result = new HashSet<>();
        synchronized (endPointSessionMapLock) {
            Set<Session> sessions = endpointSessionMap.get(endpoint);
            if (sessions != null) {
                result.addAll(sessions);
            }
        }
        return result;
    }
}
