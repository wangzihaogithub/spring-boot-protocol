package com.github.netty.springboot.server;

import com.github.netty.protocol.servlet.DispatcherChannelHandler;
import com.github.netty.protocol.servlet.ServletHttpExchange;
import com.github.netty.protocol.servlet.ServletHttpServletRequest;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.websocket.NettyMessageToWebSocketRunnable;
import com.github.netty.protocol.servlet.websocket.WebSocketServerContainer;
import com.github.netty.protocol.servlet.websocket.WebSocketServerHandshaker13Extension;
import com.github.netty.protocol.servlet.websocket.WebSocketSession;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.standard.AbstractStandardUpgradeStrategy;
import org.springframework.web.socket.server.standard.ServerEndpointRegistration;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.security.Principal;
import java.util.*;

/**
 * Websocket version number: the version number of draft 8 to draft 12 is 8, and the version number of draft 13 and later is the same as the draft number
 *
 * @author wangzihao
 */
public class NettyRequestUpgradeStrategy extends AbstractStandardUpgradeStrategy {
    private int maxFramePayloadLength;
    private static final String[] SUPPORTED_VERSIONS = new String[]{WebSocketVersion.V13.toHttpHeaderValue()};

    public NettyRequestUpgradeStrategy() {
        this(65536);
    }

    public NettyRequestUpgradeStrategy(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    @Override
    public String[] getSupportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    @Override
    protected void upgradeInternal(ServerHttpRequest request, ServerHttpResponse response, String selectedProtocol,
                                   List<Extension> selectedExtensions, Endpoint endpoint) throws HandshakeFailureException {
        HttpServletRequest servletRequest = getHttpServletRequest(request);
        ServletHttpServletRequest httpServletRequest = ServletUtil.unWrapper(servletRequest);
        if (httpServletRequest == null) {
            throw new HandshakeFailureException(
                    "Servlet request failed to upgrade to WebSocket: " + servletRequest.getRequestURL());
        }

        WebSocketServerContainer serverContainer = getContainer(servletRequest);
        Principal principal = request.getPrincipal();
        Map<String, String> pathParams = new LinkedHashMap<>(3);

        ServerEndpointRegistration endpointConfig = new ServerEndpointRegistration(servletRequest.getRequestURI(), endpoint);
        List<String> subprotocols = new ArrayList<>();
        subprotocols.add("*");
        if (selectedProtocol != null && !subprotocols.contains(selectedProtocol)) {
            subprotocols.add(selectedProtocol);
        }
        endpointConfig.setSubprotocols(subprotocols);
        if (selectedExtensions != null) {
            endpointConfig.setExtensions(selectedExtensions);
        }

        try {
            handshakeToWebsocket(httpServletRequest, selectedProtocol, maxFramePayloadLength, principal,
                    selectedExtensions, pathParams, endpoint,
                    endpointConfig, serverContainer);
        } catch (Exception e) {
            throw new HandshakeFailureException(
                    "Servlet request failed to upgrade to WebSocket: " + servletRequest.getRequestURL(), e);
        }
    }

    @Override
    protected List<WebSocketExtension> getInstalledExtensions(WebSocketContainer container) {
        List<WebSocketExtension> result = new ArrayList<>();
        for (Extension extension : container.getInstalledExtensions()) {
            Map<String, String> parameters = new LinkedCaseInsensitiveMap<>(Locale.ENGLISH);
            for (Extension.Parameter parameter : extension.getParameters()) {
                parameters.put(parameter.getName(), parameter.getValue());
            }
            result.add(new WebSocketExtension(extension.getName(), parameters));
        }
        return result;
    }

    @Override
    protected WebSocketServerContainer getContainer(HttpServletRequest request) {
        return (WebSocketServerContainer) super.getContainer(request);
    }

    /**
     * The WebSocket handshake
     *
     * @param servletRequest        servletRequest
     * @param subprotocols          subprotocols
     * @param maxFramePayloadLength maxFramePayloadLength
     * @param userPrincipal         userPrincipal
     * @param negotiatedExtensions  negotiatedExtensions
     * @param pathParameters        pathParameters
     * @param localEndpoint         localEndpoint
     * @param endpointConfig        endpointConfig
     * @param webSocketContainer    webSocketContainer
     */
    protected void handshakeToWebsocket(ServletHttpServletRequest servletRequest, String subprotocols, int maxFramePayloadLength, Principal userPrincipal,
                                        List<Extension> negotiatedExtensions, Map<String, String> pathParameters,
                                        Endpoint localEndpoint, ServerEndpointConfig endpointConfig, WebSocketServerContainer webSocketContainer) {
        FullHttpRequest nettyRequest = convertFullHttpRequest(servletRequest);
        ServletHttpExchange exchange = servletRequest.getHttpExchange();
        exchange.setWebsocket(true);
        String queryString = servletRequest.getQueryString();
        String httpSessionId = servletRequest.getRequestedSessionId();
        String webSocketURL = getWebSocketLocation(servletRequest);
        Map<String, List<String>> requestParameterMap = getRequestParameterMap(servletRequest);

        WebSocketServerHandshaker13Extension wsHandshaker = new WebSocketServerHandshaker13Extension(webSocketURL, subprotocols, true, maxFramePayloadLength);
        ChannelFuture handshakelFuture = wsHandshaker.handshake(exchange.getChannelHandlerContext().channel(), nettyRequest);
        handshakelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                DispatcherChannelHandler.setMessageToRunnable(channel, new NettyMessageToWebSocketRunnable(DispatcherChannelHandler.getMessageToRunnable(channel)));
                WebSocketSession websocketSession = new WebSocketSession(
                        channel, webSocketContainer, wsHandshaker,
                        requestParameterMap,
                        queryString, userPrincipal, httpSessionId,
                        negotiatedExtensions, pathParameters, localEndpoint, endpointConfig);

                WebSocketSession.setSession(channel, websocketSession);

                localEndpoint.onOpen(websocketSession, endpointConfig);
            } else {
                logger.warn("The Websocket handshake failed : " + webSocketURL, future.cause());
            }
        });
    }

    private FullHttpRequest convertFullHttpRequest(ServletHttpServletRequest request) {
        HttpRequest nettyRequest = request.getNettyRequest();
        if (nettyRequest instanceof FullHttpRequest) {
            return (FullHttpRequest) nettyRequest;
        }
        return new DefaultFullHttpRequest(nettyRequest.protocolVersion(), nettyRequest.method(), nettyRequest.uri(), Unpooled.buffer(0), nettyRequest.headers(), EmptyHttpHeaders.INSTANCE);
    }

    protected Map<String, List<String>> getRequestParameterMap(HttpServletRequest request) {
        MultiValueMap<String, String> requestParameterMap = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            for (String value : entry.getValue()) {
                requestParameterMap.add(entry.getKey(), value);
            }
        }
        return requestParameterMap;
    }

    protected String getWebSocketLocation(HttpServletRequest req) {
        String host = req.getHeader(HttpHeaderConstants.HOST.toString());
        if (host == null || host.isEmpty()) {
            host = req.getServerName();
        }
        String scheme = req.isSecure() ? "wss://" : "ws://";
        return scheme + host + req.getRequestURI();
    }

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }
}
