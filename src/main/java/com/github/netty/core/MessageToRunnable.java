package com.github.netty.core;

import io.netty.channel.ChannelHandlerContext;

/**
 * Convert the IO message to Runnable
 * Life cycle connection
 *
 * @author wangzihao
 */
@FunctionalInterface
public interface MessageToRunnable {

    /**
     * Create a message handler IO task
     *
     * @param context The connection
     * @param msg     IO messages (attention! : no automatic release, manual release is required)
     * @return Runnable
     */
    Runnable onMessage(ChannelHandlerContext context, Object msg);

    /**
     * Create a error handler IO task
     *
     * @param context   The connection
     * @param throwable Throwable
     * @return Runnable
     */
    default Runnable onError(ChannelHandlerContext context, Throwable throwable) {
        return null;
    }

    /**
     * Create a close handler IO task
     *
     * @param context The connection
     * @return Runnable
     */
    default Runnable onClose(ChannelHandlerContext context) {
        return null;
    }
}
