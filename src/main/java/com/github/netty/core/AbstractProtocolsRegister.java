package com.github.netty.core;

import io.netty.channel.Channel;

/**
 * An abstract Protocols Register
 * @author wangzihao
 */
public abstract class AbstractProtocolsRegister implements ProtocolsRegister{

    @Override
    public final void register(Channel channel) throws Exception {
        registerTo(channel);
    }

    protected void registerTo(Channel channel) throws Exception{

    }

    @Override
    public String toString() {
        return getProtocolName();
    }
}
