package com.github.netty.websocket.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * websocket接口控制器
 *
 * @author wangzihao 2022年3月19日19:48:58
 */
@Component
public class WebsocketController extends AbstractWebSocketHandler implements WebSocketConfigurer, HandshakeInterceptor {
    public static final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(WebsocketController.class);

    static {
        new ScheduledThreadPoolExecutor(1)
                .scheduleWithFixedDelay(() -> {
                    // 每秒发送消息
                    for (WebSocketSession session : WebsocketController.sessionMap.values()) {
                        try {
                            session.sendMessage(new TextMessage("服务端推送的"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("注册websocket {}", getClass());
        registry.addHandler(this, "/my-websocket")
                .addInterceptors(this).setAllowedOrigins("*");
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("握手前登录身份验证");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        log.info("握手后记录日志");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("建立链接保存会话");
        sessionMap.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket服务端关闭: 关闭连接状态: " + status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("接受来自客户端发送的文本信息: " + message.getPayload().toString());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        log.info("接受来自客户端发送的二进制信息: " + message.getPayload().toString());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("WebSocket服务端异常:连接异常信息: " + exception.getMessage());
    }

}