package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * 协议注册器
 *
 * @author acer01
 *  2018/11/11/011
 */
public interface ProtocolsRegister extends ServerListener {

    /**
     * 获取协议名称
     * @return 名称
     */
    String getProtocolName();

    /**
     * 是否支持协议
     * @param msg 本次消息
     * @return true=支持,false=不支持
     */
    boolean canSupport(ByteBuf msg);

    /**
     * 注册协议
     * @param channel TCP管道
     * @throws Exception
     */
    void register(Channel channel) throws Exception;

    /**
     * 优先级顺序
     * @return order 的值越小,说明越先被执行
     */
    int order();

}
