package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractNettyClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

public class DubboClient extends AbstractNettyClient {
    private ChannelHandler handler;

    public DubboClient() {
        super("", null);
    }

    public DubboClient(String namePre) {
        super(namePre, null);
    }

    @Override
    protected ChannelHandler newBossChannelHandler() {
        return handler;
    }

    public DubboClient handler(ChannelHandler handler) {
        this.handler = handler;
        return this;
    }

    public DubboClient handlers(Supplier<ChannelHandler[]> supplier) {
        this.handler = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(supplier.get());
            }
        };
        return this;
    }

    public DubboClient ioThreadCount(int ioThreadCount) {
        setIoThreadCount(ioThreadCount);
        return this;
    }

    public DubboClient ioRatio(int ioRatio) {
        setIoRatio(ioRatio);
        return this;
    }

    public DubboClient remoteAddress(InetSocketAddress remoteAddress) {
        super.remoteAddress = remoteAddress;
        return this;
    }
}