package com.github.netty.core;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

/**
 * An simple netty client
 *
 * @author wangzihao
 * 2020/2/27/018
 */
public class SimpleNettyClient extends AbstractNettyClient {
    private ChannelHandler handler;

    public SimpleNettyClient(String namePre) {
        super(namePre, null);
    }

    @Override
    protected ChannelHandler newBossChannelHandler() {
        return handler;
    }

    public SimpleNettyClient handler(ChannelHandler handler) {
        this.handler = handler;
        return this;
    }

    public SimpleNettyClient handlers(Supplier<ChannelHandler[]> supplier) {
        this.handler = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(supplier.get());
            }
        };
        return this;
    }

    public SimpleNettyClient ioThreadCount(int ioThreadCount) {
        setIoThreadCount(ioThreadCount);
        return this;
    }

    public SimpleNettyClient ioRatio(int ioRatio) {
        setIoRatio(ioRatio);
        return this;
    }

    public SimpleNettyClient remoteAddress(InetSocketAddress remoteAddress) {
        super.remoteAddress = remoteAddress;
        return this;
    }

}
