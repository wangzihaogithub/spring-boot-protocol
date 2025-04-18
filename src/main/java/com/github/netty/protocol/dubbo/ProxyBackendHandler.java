package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.Collection;

@ChannelHandler.Sharable
public class ProxyBackendHandler extends AbstractChannelHandler<ByteBuf, ByteBuf> {
    private final Collection<String> applicationNames;
    private final Channel frontendChannel;
    private Channel backendChannel;
    private final byte serializationProtoId;
    private long requestId;

    public ProxyBackendHandler(Collection<String> applicationNames, Channel frontendChannel,
                               byte serializationProtoId, long requestId) {
        super(false);
        this.applicationNames = applicationNames;
        this.frontendChannel = frontendChannel;
        this.serializationProtoId = serializationProtoId;
        this.requestId = requestId + 1;
    }

    public Collection<String> getApplicationNames() {
        return applicationNames;
    }

    public Channel getBackendChannel() {
        return backendChannel;
    }

    public Channel getFrontendChannel() {
        return frontendChannel;
    }

    /**
     * 向后端写入一个无需回复的心跳请求
     *
     * @param ctx ctx
     */
    protected void writeHeartbeatRequest(ChannelHandlerContext ctx) {
        ByteBuf request = DubboPacket.buildHeartbeatPacket(ctx.alloc(),
                serializationProtoId, requestId++, Constant.STATUS_NA, true, false);
        ctx.channel().writeAndFlush(request).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.backendChannel = ctx.channel();
    }

    @Override
    protected void onReaderIdle(ChannelHandlerContext ctx) {
        writeHeartbeatRequest(ctx);
        if (logger.isDebugEnabled()) {
            logger.debug("ProxyBackendHandler onReaderIdle writeHeartbeatRequest {} , {}", applicationNames, ctx.channel());
        }
    }

    @Override
    protected void onWriterIdle(ChannelHandlerContext ctx) {
        writeHeartbeatRequest(ctx);
        if (logger.isDebugEnabled()) {
            logger.debug("ProxyBackendHandler onWriterIdle writeHeartbeatRequest {} , {}", applicationNames, ctx.channel());
        }
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