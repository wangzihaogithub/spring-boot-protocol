package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * Protocol Handler
 *
 * @author wangzihao
 * 2018/11/11/011
 */
public interface ProtocolHandler extends Ordered {

    /**
     * Get the protocol name
     *
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
     *
     * @param clientFirstMsg client first message
     * @return true=Support, false=no Support
     */
    boolean canSupport(ByteBuf clientFirstMsg);

    /**
     * Support protocol. if receive clientFirstMsg timeout, then call canSupport(channel)
     *
     * @param channel channel
     * @return true=Support, false=no Support
     */
    default boolean canSupport(Channel channel) {
        return false;
    }

    /**
     * on out of max connection count
     *
     * @param clientFirstMsg     clientFirstMsg
     * @param tcpChannel         tcpChannel
     * @param currentConnections currentConnections
     * @param maxConnections     maxConnections
     * @return boolean. false=discard, true=keep handle
     */
    default boolean onOutOfMaxConnection(ByteBuf clientFirstMsg, TcpChannel tcpChannel,
                                         int currentConnections,
                                         int maxConnections) {
        return false;
    }

    /**
     * add protocol pipeline support
     *
     * @param channel        TCP channel
     * @param clientFirstMsg clientFirstMsg
     * @throws Exception Exception
     */
    void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception;

    /**
     * default Priority order 0
     *
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    @Override
    default int getOrder() {
        return 0;
    }

}
