package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

@ChannelHandler.Sharable
public class ProxyBackendHandler extends AbstractChannelHandler<ByteBuf, ByteBuf> {
    private final List<String> serviceNames;
    private final Channel frontendChannel;
    private Channel backendChannel;

    public ProxyBackendHandler(List<String> serviceNames, Channel frontendChannel) {
        super(false);
        this.serviceNames = serviceNames;
        this.frontendChannel = frontendChannel;
    }

    public List<String> getServiceNames() {
        return serviceNames;
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