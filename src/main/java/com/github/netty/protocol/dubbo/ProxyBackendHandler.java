package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

public class ProxyBackendHandler extends AbstractChannelHandler<ByteBuf, ByteBuf> {
    private final String serviceName;
    private final Channel frontendChannel;
    private Channel backendChannel;

    public ProxyBackendHandler(String serviceName, Channel frontendChannel) {
        super(false);
        this.serviceName = serviceName;
        this.frontendChannel = frontendChannel;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Channel getBackendChannel() {
        return backendChannel;
    }

    public Channel getFrontendChannel() {
        return frontendChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.backendChannel = ctx.channel();
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        frontendChannel.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        frontendChannel.flush();
    }
}