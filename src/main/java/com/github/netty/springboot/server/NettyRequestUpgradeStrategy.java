package com.github.netty.springboot.server;

import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.core.util.Wrapper;
import com.github.netty.protocol.servlet.ServletChannelHandler;
import com.github.netty.protocol.servlet.ServletHttpServletRequest;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.websocket.NettyMessageToWebSocketRunnable;
import com.github.netty.protocol.servlet.websocket.WebSocketServerContainer;
import com.github.netty.protocol.servlet.websocket.WebSocketServerHandshaker13Extension;
import com.github.netty.protocol.servlet.websocket.WebSocketSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.standard.AbstractStandardUpgradeStrategy;
import org.springframework.web.socket.server.standard.ServerEndpointRegistration;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Websocket version number: the version number of draft 8 to draft 12 is 8, and the version number of draft 13 and later is the same as the draft number
 * @author wangzihao
 */
public class NettyRequestUpgradeStrategy extends AbstractStandardUpgradeStrategy {
    public static final String SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE = "javax.websocket.server.ServerContainer";
    private int maxFramePayloadLength;

    public NettyRequestUpgradeStrategy() {
        this(64 * 1024);
    }

    public NettyRequestUpgradeStrategy(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    @Override
    public String[] getSupportedVersions() {
        return new String[]{WebSocketVersion.V13.toHttpHeaderValue()};
    }

    @Override
    protected void upgradeInternal(ServerHttpRequest request, ServerHttpResponse response, String selectedProtocol,
                                   List<Extension> selectedExtensions, Endpoint endpoint) throws HandshakeFailureException {
        HttpServletRequest servletRequest = getHttpServletRequest(request);
        ServletHttpServletRequest httpServletRequest = ServletUtil.unWrapper(servletRequest);
        if(httpServletRequest == null) {
            throw new HandshakeFailureException(
                    "Servlet request failed to upgrade to WebSocket: " + servletRequest.getRequestURL());
        }

        WebSocketServerContainer serverContainer = getContainer(servletRequest);
        Principal principal = request.getPrincipal();
        Map<String, String> pathParams = new HashMap<>(3);

        ServerEndpointRegistration endpointConfig = new ServerEndpointRegistration(servletRequest.getRequestURI(), endpoint);
        endpointConfig.setSubprotocols(Arrays.asList(WebSocketServerHandshaker.SUB_PROTOCOL_WILDCARD,selectedProtocol));
        if(selectedExtensions != null) {
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
    protected WebSocketServerContainer getContainer(HttpServletRequest request) {
        ServletContext servletContext = request.getServletContext();
        Object websocketServerContainer = servletContext.getAttribute(SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        if (websocketServerContainer == null || !(websocketServerContainer instanceof WebSocketServerContainer)) {
            websocketServerContainer = new WebSocketServerContainer();
            servletContext.setAttribute(SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE, websocketServerContainer);
        }
        return (WebSocketServerContainer) websocketServerContainer;
    }

    /**
     *  The WebSocket handshake
     * @param servletRequest servletRequest
     * @param subprotocols subprotocols
     * @param maxFramePayloadLength maxFramePayloadLength
     * @param userPrincipal userPrincipal
     * @param negotiatedExtensions negotiatedExtensions
     * @param pathParameters pathParameters
     * @param localEndpoint localEndpoint
     * @param endpointConfig endpointConfig
     * @param webSocketContainer webSocketContainer
     */
    protected void handshakeToWebsocket(ServletHttpServletRequest servletRequest, String subprotocols, int maxFramePayloadLength, Principal userPrincipal,
                                      List<Extension> negotiatedExtensions, Map<String, String> pathParameters,
                                      Endpoint localEndpoint, EndpointConfig endpointConfig, WebSocketServerContainer webSocketContainer){
        FullHttpRequest nettyRequest = servletRequest.getNettyRequest();
        ChannelHandlerContext channelContext = Wrapper.unwrap(servletRequest.getServletHttpExchange().getChannelHandlerContext());

        String queryString = servletRequest.getQueryString();
        String httpSessionId = servletRequest.getSession().getId();
        String webSocketURL = getWebSocketLocation(servletRequest);
        Map<String,List<String>> requestParameterMap = getRequestParameterMap(servletRequest);

        WebSocketServerHandshaker wsHandshaker = new WebSocketServerHandshaker13Extension(webSocketURL,subprotocols,true,maxFramePayloadLength);
        ChannelFuture handshakelFuture = wsHandshaker.handshake(channelContext.channel(), nettyRequest);
        handshakelFuture.addListener((ChannelFutureListener) future -> {
            if(future.isSuccess()) {
                Channel channel = future.channel();
                ServletChannelHandler.setMessageToRunnable(channel, new NettyMessageToWebSocketRunnable(ServletChannelHandler.getMessageToRunnable(channel)));
                WebSocketSession websocketSession = new WebSocketSession(
                        channel, webSocketContainer, wsHandshaker,
                        requestParameterMap,
                        queryString, userPrincipal, httpSessionId,
                        negotiatedExtensions, pathParameters, localEndpoint);

                WebSocketSession.setSession(channel, websocketSession);

                localEndpoint.onOpen(websocketSession, endpointConfig);
            }else {
                logger.error("The Websocket handshake failed : "+ webSocketURL, future.cause());
            }
        });
    }

    protected Map<String,List<String>> getRequestParameterMap(HttpServletRequest request){
        MultiValueMap<String,String> requestParameterMap = new LinkedMultiValueMap<>();
        for(Map.Entry<String,String[]> entry : request.getParameterMap().entrySet()){
            for(String value : entry.getValue()){
                requestParameterMap.add(entry.getKey(),value);
            }
        }
        return requestParameterMap;
    }

    protected String getWebSocketLocation(HttpServletRequest req) {
        String host = req.getHeader(HttpHeaderConstants.HOST.toString());
        if(host == null || host.isEmpty()){
            host = req.getServerName();
        }
        String scheme = req.isSecure()? "wss://" : "ws://";
        return scheme + host + req.getRequestURI();
    }

    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }
}
