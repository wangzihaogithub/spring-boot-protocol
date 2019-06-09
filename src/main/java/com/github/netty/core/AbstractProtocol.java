package com.github.netty.core;

import io.netty.channel.Channel;

/**
 * An abstract Protocols Register
 * @author wangzihao
 */
public abstract class AbstractProtocol implements ProtocolHandler,ServerListener {

    @Override
    public final void supportPipeline(Channel channel) throws Exception {
        onSupportPipeline(channel);
    }

    protected void onSupportPipeline(Channel channel) throws Exception{

    }

    @Override
    public String toString() {
        return getProtocolName();
    }
}
