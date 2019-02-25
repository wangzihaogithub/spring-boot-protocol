package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * Protocol register
 * @author wangzihao
 *  2018/11/11/011
 */
public interface ProtocolsRegister extends ServerListener {

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
     * registration protocol
     * @param channel TCP channel
     * @throws Exception Exception
     */
    void register(Channel channel) throws Exception;

    /**
     * Priority order
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    int order();

}
