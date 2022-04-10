package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * Protocol Handler
 * @author wangzihao
 *  2018/11/11/011
 */
public interface ProtocolHandler extends Ordered{

    /**
     * Get the protocol name
     * @return name
     */
    default String getProtocolName() {
        String name = getClass().getSimpleName();
        if (name.isEmpty()) {
            name = getClass().getName();
        }
        return name;
    }

    /**
     * Support protocol
     * @param clientFirstMsg client first message
     * @return true=Support, false=no Support
     */
    boolean canSupport(ByteBuf clientFirstMsg);

    /**
     * Support protocol
     * @param channel channel
     * @return true=Support, false=no Support
     */
    default boolean canSupport(Channel channel){
        return false;
    }

    /**
     * add protocol pipeline support
     * @param channel TCP channel
     * @throws Exception Exception
     */
    void addPipeline(Channel channel) throws Exception;

    /**
     * default Priority order 0
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    @Override
    default int getOrder(){
        return 0;
    }

}
