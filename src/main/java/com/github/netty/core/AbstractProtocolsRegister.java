package com.github.netty.core;

import io.netty.channel.Channel;

/**
 * 抽象的协议注册器
 * @author 84215
 */
public abstract class AbstractProtocolsRegister implements ProtocolsRegister{

    @Override
    public final void register(Channel channel) throws Exception {
        registerTo(channel);
    }

    protected void registerTo(Channel channel) throws Exception{

    }


}
