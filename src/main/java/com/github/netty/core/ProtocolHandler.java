package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * Protocol Handler
 * @author wangzihao
 *  2018/11/11/011
 */
public interface ProtocolHandler {

    /**
     * Get the protocol name
     * @return name
     */
    String getProtocolName();

    /**
     * Support protocol
     * @param msg This message
     * @return true=Support, false=no Support
     */
    boolean canSupport(ByteBuf msg);

    /**
     * protocol pipeline support
     * @param channel TCP channel
     * @throws Exception Exception
     */
    void supportPipeline(Channel channel) throws Exception;

    /**
     * Priority order
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    int order();

}
