package com.github.netty.protocol.servlet.websocket;

import javax.websocket.CloseReason;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import java.nio.ByteBuffer;

public interface WebSocketHandler {

    default void afterConnectionEstablished(Session session) throws Exception {
    }

    default void handleTextMessage(Session session, String message, boolean isLast) throws Exception {
    }

    default void handleBinaryMessage(Session session, ByteBuffer message, boolean isLast) throws Exception {
    }

    default void handlePongMessage(Session session, PongMessage message, boolean isLast) throws Exception {
    }

    default void handleTransportError(Session session, Throwable exception) throws Exception {
    }

    default void afterConnectionClosed(Session session, CloseReason closeStatus) throws Exception {
    }

}

