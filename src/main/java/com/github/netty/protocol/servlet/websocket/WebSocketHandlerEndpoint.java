package com.github.netty.protocol.servlet.websocket;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import io.netty.util.internal.PlatformDependent;

import javax.websocket.*;
import java.nio.ByteBuffer;
import java.util.Objects;

public class WebSocketHandlerEndpoint extends Endpoint {
    private static final LoggerX logger = LoggerFactoryX.getLogger(WebSocketHandlerEndpoint.class);

    private WebSocketHandler handler;

    public WebSocketHandlerEndpoint(WebSocketHandler handler) {
        this.handler = Objects.requireNonNull(handler, "WebSocketHandler");
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        session.addMessageHandler(new MessageHandler.Partial<Object>() {
            @Override
            public void onMessage(Object message, boolean last) {
                try {
                    if (message instanceof PongMessage) {
                        handler.handlePongMessage(session, (PongMessage) message, last);
                    } else if (message instanceof String) {
                        handler.handleTextMessage(session, (String) message, last);
                    } else if (message instanceof ByteBuffer) {
                        handler.handleBinaryMessage(session, (ByteBuffer) message, last);
                    }
                } catch (Exception e) {
                    PlatformDependent.throwException(e);
                }
            }
        });
        try {
            this.handler.afterConnectionEstablished(session);
        } catch (Exception exception) {
            tryCloseWithError(session, exception);
        }
    }

    @Override
    public void onError(Session session, Throwable exception) {
        try {
            this.handler.handleTransportError(session, exception);
        } catch (Exception ex) {
            tryCloseWithError(session, ex);
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        try {
            this.handler.afterConnectionClosed(session, closeReason);
        } catch (Exception ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Unhandled on-close exception for " + session, ex);
            }
        }
    }

    public static void tryCloseWithError(Session session, Throwable exception) {
        if (logger.isErrorEnabled()) {
            logger.error("Closing session due to exception for " + session, exception);
        }
        if (session.isOpen()) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, null));
            } catch (Throwable e) {
                // ignore
            }
        }
    }

}