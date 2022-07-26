package com.github.netty.protocol.servlet.websocket;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.*;
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

    public NettyMessageToWebSocketRunnable(MessageToRunnable parent) {
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
                    onWebsocketMessage(wsSession, frame, IOUtil.heap(frame.content()), PongMessage.class);
                    return;
                }

                // Binary message
                if (frame instanceof BinaryWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, IOUtil.heap(frame.content()), ByteBuffer.class);
                    return;
                }

                // String message
                if (frame instanceof TextWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, ((TextWebSocketFrame) frame).text(), String.class);
                }
            } finally {
                WebsocketRunnable.this.recycle();
            }
        }

        private void onWebsocketMessage(WebSocketSession wsSession, WebSocketFrame frame, Object message, Class messageType) {
            Set<MessageHandler> messageHandlers = wsSession.getMessageHandlers();
            for (MessageHandler handler : messageHandlers) {
                if (handler instanceof MessageHandler.Partial) {
                    MessageHandler.Partial<Object> partial = ((MessageHandler.Partial<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Partial.class, partial.getClass());
                    if (typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz() == messageType) {
                        try {
                            boolean finalFragment = frame.isFinalFragment();
                            if (frame instanceof PingWebSocketFrame) {
                                ByteBuffer applicationData = ByteBuffer.wrap((byte[]) message);
                                partial.onMessage((PongMessage) () -> applicationData, finalFragment);
                            } else if (frame instanceof BinaryWebSocketFrame) {
                                partial.onMessage(ByteBuffer.wrap((byte[]) message), finalFragment);
                            } else {
                                partial.onMessage(message, finalFragment);
                            }
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
                            || typeResult.getClazz() == messageType){
                        try {
                            if (frame instanceof PingWebSocketFrame) {
                                ByteBuffer applicationData = ByteBuffer.wrap((byte[]) message);
                                whole.onMessage((PongMessage) () -> applicationData);
                            } else if (frame instanceof BinaryWebSocketFrame) {
                                whole.onMessage(ByteBuffer.wrap((byte[]) message));
                            } else {
                                whole.onMessage(message);
                            }
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
            RecyclableUtil.release(frame);
            frame = null;
        }
    }

}
