package com.github.netty.register.servlet.websocket;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.AbstractRecycler;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.TypeUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.*;

import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * websocket任务工厂
 * @author 84215
 */
public class WebSocketMessageToRunnable implements MessageToRunnable {

    private static final AbstractRecycler<WebsocketTask> RECYCLER = new AbstractRecycler<WebsocketTask>() {
        @Override
        protected WebsocketTask newInstance() {
            return new WebsocketTask();
        }
    };

    private MessageToRunnable parent;

    public WebSocketMessageToRunnable(MessageToRunnable parent) {
        this.parent = parent;
    }

    @Override
    public Runnable newRunnable(ChannelHandlerContext channelHandlerContext, Object msg) {
        if(msg instanceof WebSocketFrame) {
            WebsocketTask task = RECYCLER.getInstance();
            task.context = channelHandlerContext;
            task.frame = (WebSocketFrame) msg;
            return task;
        }
        if(parent != null){
            return parent.newRunnable(channelHandlerContext,msg);
        }
        throw new IllegalStateException("["+msg.getClass().getName()+"] 无法处理的消息数据类型");
    }

    /**
     * Websocket任务
     */
    public static class WebsocketTask implements Runnable,Recyclable {
        private ChannelHandlerContext context;
        private WebSocketFrame frame;

        @Override
        public void run() {
            try {
                WebSocketSession wsSession = WebSocketSession.getSession(context.channel());
                if (wsSession == null) {
                    return;
                }

                // 关闭消息
                if (frame instanceof CloseWebSocketFrame) {
                    wsSession.getWebSocketServerHandshaker().close(context.channel(), (CloseWebSocketFrame) frame.retain());
                    return;
                }

                // Ping消息
                if (frame instanceof PingWebSocketFrame) {
                    ByteBuffer request = frame.content().nioBuffer();
                    onWebsocketMessage(wsSession, frame, (PongMessage) () -> request);
                    return;
                }

                // 二进制消息
                if (frame instanceof BinaryWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, frame.content().nioBuffer());
                    return;
                }

                // 字符串消息
                if (frame instanceof TextWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, ((TextWebSocketFrame) frame).text());
                }
            }finally {
                WebsocketTask.this.recycle();
            }
        }

        private void onWebsocketMessage(WebSocketSession wsSession, WebSocketFrame frame, Object message){
            Class messageType = message.getClass();

            Set<MessageHandler> messageHandlers = wsSession.getMessageHandlers();
            for(MessageHandler handler : messageHandlers){
                if(handler instanceof MessageHandler.Partial){
                    MessageHandler.Partial<Object> partial =((MessageHandler.Partial<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Partial.class, partial.getClass());
                    if(typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz().isAssignableFrom(messageType)){
                        partial.onMessage(message,frame.isFinalFragment());
                    }
                    continue;
                }

                if(handler instanceof MessageHandler.Whole){
                    MessageHandler.Whole<Object> whole =((MessageHandler.Whole<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Whole.class,whole.getClass());
                    if(typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz().isAssignableFrom(messageType)){
                        whole.onMessage(message);
                    }
                }
            }
        }

        @Override
        public void recycle() {
            if(context instanceof Recyclable) {
                ((Recyclable) context).recycle();
            }
            context = null;
            frame = null;
        }

    }


}
