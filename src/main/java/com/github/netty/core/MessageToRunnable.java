package com.github.netty.core;

import io.netty.channel.ChannelHandlerContext;

/**
 * 将IO消息转换为Runnable
 * @author 84215
 */
@FunctionalInterface
public interface MessageToRunnable {

    /**
     * 新建一个IO任务
     * @param context 连接
     * @param msg IO消息 (注意! : 不会自动释放, 需要手动释放)
     * @return
     */
    Runnable newRunnable(ChannelHandlerContext context, Object msg);

}
