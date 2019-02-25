package com.github.netty.protocol.servlet;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.MessageToRunnable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.util.concurrent.Executor;

/**
 * Servlet processor (portal to the server)
 * @author wangzihao
 *  2018/7/1/001
 */
@ChannelHandler.Sharable
public class ServletChannelHandler extends AbstractChannelHandler<Object,Object> {
    private Executor dispatcherExecutor;
    private NettyMessageToServletRunnable httpMessageToServletRunnable;
    public static final AttributeKey<MessageToRunnable> CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE = AttributeKey.valueOf(MessageToRunnable.class + "#MessageToRunnable");

    public ServletChannelHandler(ServletContext servletContext, Executor dispatcherExecutor) {
        super(false);
        this.httpMessageToServletRunnable = new NettyMessageToServletRunnable(servletContext);
        this.dispatcherExecutor = dispatcherExecutor;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext context, Object msg) throws Exception {
        MessageToRunnable messageToRunnable = getMessageToRunnable(context.channel());
        if(messageToRunnable == null){
            messageToRunnable = httpMessageToServletRunnable;
            setMessageToRunnable(context.channel(),messageToRunnable);
        }

        Runnable task = messageToRunnable.newRunnable(context,msg);
        if(dispatcherExecutor != null){
            dispatcherExecutor.execute(task);
        }else {
            task.run();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        saveAndClearSession(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.toString());
        saveAndClearSession(ctx);
        ctx.channel().close();
    }

    /**
     * Save and clear the session
     * @param ctx
     */
    protected void saveAndClearSession(ChannelHandlerContext ctx){
        ServletHttpSession httpSession = ServletHttpObject.getSession(ctx);
        if(httpSession != null) {
            if (httpSession.isValid()) {
                httpSession.save();
                logger.info("saveHttpSession : sessionId="+httpSession.getId());
            } else if (httpSession.getId() != null) {
                httpSession.remove();
                logger.info("removeHttpSession : sessionId="+httpSession.getId());
            }
            httpSession.clear();
        }
    }

    /**
     * Place the IO task package factory class on this connection
     * @param channel
     * @param messageToRunnable
     */
    public static void setMessageToRunnable(Channel channel, MessageToRunnable messageToRunnable){
        channel.attr(CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE).set(messageToRunnable);
    }

    /**
     * Pull out the IO task package factory class on this connection
     * @param channel
     * @return
     */
    public static MessageToRunnable getMessageToRunnable(Channel channel){
        MessageToRunnable taskFactory = channel.attr(CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE).get();
        return taskFactory;
    }

}
