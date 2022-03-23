package com.github.netty.protocol.servlet.websocket;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.core.util.TypeUtil;
import com.github.netty.protocol.servlet.ServletHttpExchange;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.*;

import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * WebSocketMessageToRunnable
 * Life cycle connection
 *
 * @author wangzihao
 */
public class NettyMessageToWebSocketRunnable implements MessageToRunnable {
    private static final Recycler<WebsocketRunnable> RECYCLER = new Recycler<>(WebsocketRunnable::new);
    private MessageToRunnable parent;
    private ServletHttpExchange exchange;

    public NettyMessageToWebSocketRunnable(MessageToRunnable parent, ServletHttpExchange exchange) {
        this.parent = parent;
    }

    @Override
    public Runnable onMessage(ChannelHandlerContext context, Object msg) {
        if (msg instanceof WebSocketFrame) {
            WebsocketRunnable task = RECYCLER.getInstance();
            task.context = context;
            task.frame = (WebSocketFrame) msg;
            return task;
        }
        if (parent != null) {
            return parent.onMessage(context, msg);
        }
        throw new IllegalStateException("[" + msg.getClass().getName() + "] Message data type that cannot be processed");
    }

    @Override
    public Runnable onClose(ChannelHandlerContext context) {
        if (exchange != null) {
            exchange.setWebsocket(false);
        }
        if (parent != null) {
            return parent.onClose(context);
        }
        return null;
    }

    /**
     * Websocket task
     */
    public static class WebsocketRunnable implements Runnable, Recyclable {
        private ChannelHandlerContext context;
        private WebSocketFrame frame;

        public WebSocketSession getWebSocketSession() {
            return WebSocketSession.getSession(context.channel());
        }

        public WebSocketFrame getFrame() {
            return frame;
        }

        public void setFrame(WebSocketFrame frame) {
            this.frame = frame;
        }

        public ChannelHandlerContext getContext() {
            return context;
        }

        @Override
        public void run() {
            try {
                WebSocketSession wsSession = getWebSocketSession();
                if (wsSession == null) {
                    return;
                }

                // Close the message
                if (frame instanceof CloseWebSocketFrame) {
                    wsSession.closeByClient((CloseWebSocketFrame) frame);
                    return;
                }

                // Ping message
                if (frame instanceof PingWebSocketFrame) {
                    ByteBuffer request = frame.content().nioBuffer();
                    onWebsocketMessage(wsSession, frame, (PongMessage) () -> request);
                    return;
                }

                // Binary message
                if (frame instanceof BinaryWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, frame.content().nioBuffer());
                    return;
                }

                // String message
                if (frame instanceof TextWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, ((TextWebSocketFrame) frame).text());
                }
            } finally {
                WebsocketRunnable.this.recycle();
            }
        }

        private void onWebsocketMessage(WebSocketSession wsSession, WebSocketFrame frame, Object message) {
            Class messageType = message.getClass();

            Set<MessageHandler> messageHandlers = wsSession.getMessageHandlers();
            for (MessageHandler handler : messageHandlers) {
                if (handler instanceof MessageHandler.Partial) {
                    MessageHandler.Partial<Object> partial = ((MessageHandler.Partial<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Partial.class, partial.getClass());
                    if (typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz().isAssignableFrom(messageType)) {
                        try {
                            partial.onMessage(message, frame.isFinalFragment());
                        } catch (Throwable e) {
                            wsSession.onError(e);
                        }
                    }
                    continue;
                }

                if (handler instanceof MessageHandler.Whole) {
                    MessageHandler.Whole<Object> whole = ((MessageHandler.Whole<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Whole.class, whole.getClass());
                    if (typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz().isAssignableFrom(messageType)) {
                        try {
                            whole.onMessage(message);
                        } catch (Throwable e) {
                            wsSession.onError(e);
                        }
                    }
                }
            }
        }

        @Override
        public void recycle() {
            if (context instanceof Recyclable) {
                ((Recyclable) context).recycle();
            }
            context = null;
            frame = null;
        }
    }

}
