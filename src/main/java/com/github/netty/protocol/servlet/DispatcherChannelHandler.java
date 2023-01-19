package com.github.netty.protocol.servlet;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.servlet.util.Protocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * Servlet processor (portal to the server)
 *
 * @author wangzihao
 * 2018/7/1/001
 */
public class DispatcherChannelHandler extends AbstractChannelHandler<Object, Object> {
    public static final AttributeKey<MessageToRunnable> CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE = AttributeKey.valueOf(MessageToRunnable.class + "#MessageToRunnable");
    protected final ServletContext servletContext;
    protected final long maxContentLength;
    protected final Protocol protocol;
    protected final boolean ssl;

    public DispatcherChannelHandler(ServletContext servletContext, long maxContentLength, Protocol protocol, boolean ssl) {
        super(false);
        this.servletContext = servletContext;
        this.maxContentLength = maxContentLength;
        this.protocol = protocol;
        this.ssl = ssl;
    }

    /**
     * Place the IO task package factory class on this connection
     *
     * @param channel           channel
     * @param messageToRunnable messageToRunnable
     */
    public static void setMessageToRunnable(Channel channel, MessageToRunnable messageToRunnable) {
        channel.attr(CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE).set(messageToRunnable);
    }

    /**
     * Pull out the IO task package factory class on this connection
     *
     * @param channel channel
     * @return MessageToRunnable
     */
    public static MessageToRunnable getMessageToRunnable(Channel channel) {
        MessageToRunnable taskFactory = channel.attr(CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE).get();
        return taskFactory;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // Dynamic binding protocol for switching protocol
        DispatcherChannelHandler.setMessageToRunnable(ctx.channel(), new NettyMessageToServletRunnable(servletContext, maxContentLength, protocol, ssl));
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext context, Object msg) {
        try {
            MessageToRunnable messageToRunnable = getMessageToRunnable(context.channel());
            if (messageToRunnable != null) {
                Runnable runnable = messageToRunnable.onMessage(context, msg);
                if (runnable != null) {
                    run(runnable);
                }
            } else {
                logger.warn("no handler message = {}", msg.getClass());
                RecyclableUtil.release(msg);
            }
        } catch (Exception e) {
            RecyclableUtil.release(msg);
            context.pipeline().fireExceptionCaught(e);
        }
    }

    protected void run(Runnable task) {
        switch (protocol) {
            case h2c:
            case h2: {
                servletContext.getExecutor().execute(task);
                break;
            }
            default: {
                Executor executor = servletContext.getAsyncExecutor();
                if (executor != null) {
                    executor.execute(task);
                } else {
                    task.run();
                }
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        MessageToRunnable messageToRunnable = getMessageToRunnable(context.channel());
        if (messageToRunnable != null) {
            Runnable runnable = messageToRunnable.onClose(context);
            if (runnable != null) {
                run(runnable);
            }
        }
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        if (cause instanceof Http2Exception) {

        } else if (cause.getClass() != IOException.class) {
            logger.warn("handler exception. case={}, channel={}", cause.toString(), context.channel(), cause);
        }
        MessageToRunnable messageToRunnable = getMessageToRunnable(context.channel());
        if (messageToRunnable != null) {
            Runnable runnable = messageToRunnable.onError(context, cause);
            if (runnable != null) {
                run(runnable);
            }
        }
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }
}
