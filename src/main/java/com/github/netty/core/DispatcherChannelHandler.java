package com.github.netty.core;

import com.github.netty.core.util.RecyclableUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Servlet processor (portal to the server)
 * @author wangzihao
 *  2018/7/1/001
 */
@ChannelHandler.Sharable
public class DispatcherChannelHandler extends AbstractChannelHandler<Object,Object> {
    private final Supplier<Executor> dispatcherExecutor;
    public static final AttributeKey<MessageToRunnable> CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE = AttributeKey.valueOf(MessageToRunnable.class + "#MessageToRunnable");

    public DispatcherChannelHandler(Supplier<Executor> dispatcherExecutor) {
        super(false);
        this.dispatcherExecutor = dispatcherExecutor;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext context, Object msg) throws Exception {
        MessageToRunnable messageToRunnable = getMessageToRunnable(context.channel());
        if(messageToRunnable != null) {
            Runnable runnable = messageToRunnable.onMessage(context, msg);
            if (runnable != null) {
                run(runnable);
            }
        }else {
            logger.error("no handler message = {}",msg.getClass());
            RecyclableUtil.release(msg);
        }
    }

    protected void run(Runnable task){
        Executor executor = getExecutor();
        if(executor != null) {
            executor.execute(task);
        }else {
            task.run();
        }
    }

    private Executor getExecutor(){
        if(dispatcherExecutor != null){
            try {
                return dispatcherExecutor.get();
            }catch (Exception e){
                logger.warn("get dispatcherExecutor failure.  msg = {}",e.getMessage(),e);
            }
        }
        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(cause.getClass() != IOException.class){
            logger.error("handler exception. case={}, channel={}",cause.toString(),ctx.channel(),cause);
        }
        ctx.close();
    }

    /**
     * Place the IO task package factory class on this connection
     * @param channel channel
     * @param messageToRunnable messageToRunnable
     */
    public static void setMessageToRunnable(Channel channel, MessageToRunnable messageToRunnable){
        channel.attr(CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE).set(messageToRunnable);
    }

    /**
     * Pull out the IO task package factory class on this connection
     * @param channel channel
     * @return MessageToRunnable
     */
    public static MessageToRunnable getMessageToRunnable(Channel channel){
        MessageToRunnable taskFactory = channel.attr(CHANNEL_ATTR_KEY_MESSAGE_TO_RUNNABLE).get();
        return taskFactory;
    }

}
