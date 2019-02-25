package com.github.netty.core;

import io.netty.channel.ChannelHandlerContext;

/**
 * Convert the IO message to Runnable
 * @author wangzihao
 */
@FunctionalInterface
public interface MessageToRunnable {

    /**
     * Create a new IO task
     * @param context The connection
     * @param msg IO messages (attention! : no automatic release, manual release is required)
     * @return Runnable
     */
    Runnable newRunnable(ChannelHandlerContext context, Object msg);

}
