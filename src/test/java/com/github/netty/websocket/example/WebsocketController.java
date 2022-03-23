package com.github.netty.websocket.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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
    public static final Map<String, NativeWebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(WebsocketController.class);

    static {
        new ScheduledThreadPoolExecutor(1)
                .scheduleWithFixedDelay(() -> {
                    // 每秒发送消息
                    for (NativeWebSocketSession session : WebsocketController.sessionMap.values()) {
                        if (!session.isOpen()) {
                            continue;
                        }
                        Session nativeSession = session.getNativeSession(Session.class);
                        try {
                            // 异步发送
                            ByteBuffer data = ByteBuffer.wrap("123456".getBytes());
                            nativeSession.getAsyncRemote().sendBinary(data, result -> {
                                log.info("发送是否成功 = {} {}", result.isOK(), result.getException());
                            });

                            // 同步发送
                            session.sendMessage(new TextMessage("服务端推送的"));
                        } catch (ClosedChannelException e) {
                            log.info("sendMessage() ClosedChannelException = {} ", e.toString(), e);
                        } catch (IOException e) {
                            log.info("sendMessage() IOException = {} ", e.toString(), e);
                        } catch (Exception e) {
                            log.info("sendMessage() Exception = {} ", e.toString(), e);
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
        attributes.put("request", request);
        attributes.put("response", response);
        attributes.put("wsHandler", wsHandler);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        log.info("握手后记录日志");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("建立链接保存会话");
        sessionMap.put(session.getId(), (NativeWebSocketSession) session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket服务端关闭: 关闭连接状态: " + status);
        sessionMap.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("接受来自客户端发送的文本信息: " + message.getPayload());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        log.info("接受来自客户端发送的二进制信息: " + message.getPayload().toString());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("WebSocket服务端异常:连接异常信息: " + exception.toString(), exception);
    }

}