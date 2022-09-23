package com.github.netty.protocol.servlet.websocket;

import com.github.netty.protocol.servlet.DispatcherChannelHandler;
import com.github.netty.protocol.servlet.ServletContext;
import com.github.netty.protocol.servlet.util.AntPathMatcher;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpRequest;

import javax.servlet.http.Cookie;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;
import java.nio.charset.Charset;
import java.util.*;

public class WebsocketServletUpgrader {
    private AntPathMatcher pathMatcher = new AntPathMatcher();
    private Map<String, EndpointHolder> endpointHolderMap = new LinkedHashMap<>();
    private EndpointHolder notFoundHandlerEndpointHolder = new EndpointHolder(
            WebSocketNotFoundHandlerEndpoint.INSTANCE,
            ServerEndpointConfig.Builder.create(WebSocketNotFoundHandlerEndpoint.class, "/").build());

    public boolean addHandler(String pathPattern, WebSocketHandler handler) {
        return addEndpoint(pathPattern, new WebSocketHandlerEndpoint(handler));
    }

    public boolean addEndpoint(String pathPattern, Endpoint endpoint) {
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(endpoint.getClass(), pathPattern).build();
        return endpointHolderMap.put(pathPattern, new EndpointHolder(endpoint, config)) != null;
    }

    protected EndpointHolder getWebSocketHandlerHolder(HttpRequest request) {
        String path = request.uri();
        for (Map.Entry<String, EndpointHolder> entry : endpointHolderMap.entrySet()) {
            String pattern = entry.getKey();
            if (pathMatcher.match(pattern, path, "*")) {
                return entry.getValue();
            }
        }
        return notFoundHandlerEndpointHolder;
    }

    public void upgradeWebsocket(ServletContext servletContext,
                                 ChannelHandlerContext ctx,
                                 HttpRequest request, boolean secure,
                                 int maxFramePayloadLength) {
        ChannelPipeline pipeline = ctx.pipeline();
        String webSocketURL = getWebSocketURL(request, secure);
        Map<String, List<String>> requestParameterMap = getRequestParameterMap(request);
        WebSocketServerHandshaker13Extension wsHandshaker = new WebSocketServerHandshaker13Extension(webSocketURL, null, true, maxFramePayloadLength);
        ChannelFuture handshakelFuture = wsHandshaker.handshake(pipeline.channel(), request);

        EndpointHolder holder = getWebSocketHandlerHolder(request);

        handshakelFuture.addListener((ChannelFutureListener) future -> {
            WebSocketServerContainer webSocketContainer = (WebSocketServerContainer) servletContext.getAttribute(ServletContext.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            String queryString = getQueryString(request.uri());
            String httpSessionId = getRequestedSessionId(servletContext, request.headers().get("Cookie"), requestParameterMap);
            List<Extension> extensions = new ArrayList<>(webSocketContainer.getInstalledExtensions());
            if (future.isSuccess()) {
                Channel channel = future.channel();
                DispatcherChannelHandler.setMessageToRunnable(channel, new NettyMessageToWebSocketRunnable(DispatcherChannelHandler.getMessageToRunnable(channel)));

                Endpoint localEndpoint = holder.localEndpoint;
                ServerEndpointConfig endpointConfig = holder.config;

                WebSocketSession websocketSession = new WebSocketSession(
                        channel, webSocketContainer, wsHandshaker,
                        requestParameterMap,
                        queryString, null, httpSessionId,
                        extensions, new HashMap<>(), localEndpoint, endpointConfig);

                WebSocketSession.setSession(channel, websocketSession);
                localEndpoint.onOpen(websocketSession, endpointConfig);
            } else {
                ctx.fireExceptionCaught(future.cause());
            }
        });
    }

    private String getWebSocketURL(HttpRequest request, boolean secure) {
        String host = request.headers().get(HttpHeaderConstants.HOST.toString());
        return (secure ? "wss://" : "ws://") + host + request.uri();
    }

    private String getRequestedSessionId(ServletContext servletContext, String headerCookie, Map<String, List<String>> requestParameterMap) {
        //If the user sets the sessionCookie name, the user set the sessionCookie name
        String userSettingCookieName = servletContext.getSessionCookieConfig().getName();
        String cookieSessionName = userSettingCookieName != null && userSettingCookieName.length() > 0 ?
                userSettingCookieName : HttpConstants.JSESSION_ID_COOKIE;
        String sessionId = null;
        if (headerCookie != null && !headerCookie.isEmpty()) {
            Cookie[] cookies = ServletUtil.decodeCookie(headerCookie);
            sessionId = ServletUtil.getCookieValue(cookies, cookieSessionName);
        }

        if (sessionId != null && sessionId.length() > 0) {
            return sessionId;
        }
        List<String> sessionIds = requestParameterMap.get(HttpConstants.JSESSION_ID_URL);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            sessionId = sessionIds.get(0);
        }
        return sessionId;
    }

    private String getQueryString(String requestURI) {
        String queryString;
        int queryInx = requestURI.indexOf('?');
        if (queryInx != -1) {
            queryString = requestURI.substring(queryInx + 1);
        } else {
            queryString = null;
        }
        return queryString;
    }

    protected Map<String, List<String>> getRequestParameterMap(HttpRequest request) {
        Map<String, List<String>> requestParameterMap = new LinkedHashMap<>();
        Map<String, String[]> parameterMap = new LinkedHashMap<>();
        ServletUtil.decodeByUrl(parameterMap, request.uri(), Charset.forName("utf-8"));
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            for (String value : entry.getValue()) {
                requestParameterMap.computeIfAbsent(entry.getKey(), e -> new ArrayList<>(1))
                        .add(value);
            }
        }
        return requestParameterMap;
    }

    public static class EndpointHolder {
        private Endpoint localEndpoint;
        private ServerEndpointConfig config;

        EndpointHolder(Endpoint localEndpoint, ServerEndpointConfig config) {
            this.localEndpoint = localEndpoint;
            this.config = config;
        }
    }

}
