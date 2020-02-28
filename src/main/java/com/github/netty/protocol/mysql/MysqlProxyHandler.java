package com.github.netty.protocol.mysql;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.function.Supplier;

public class MysqlProxyHandler extends AbstractChannelHandler<ByteBuf,ByteBuf> {
    private final Supplier<Channel> channelSupplier;
    public MysqlProxyHandler(Supplier<Channel> channelSupplier) {
        super(false);
        this.channelSupplier = channelSupplier;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ServerErrorPacket errorPacket = new ServerErrorPacket(
                0,3000,"#HY000".getBytes(),cause.toString());
        channelSupplier.get().writeAndFlush(errorPacket);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // directly write getClientChannel data to getMysqlChannel real mysql connection
        ByteBuf byteBuf = msg.alloc().heapBuffer(msg.readableBytes());
        msg.getBytes(0,byteBuf);
        ctx.fireChannelRead(byteBuf);
        channelSupplier.get().write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        channelSupplier.get().writeAndFlush(Unpooled.EMPTY_BUFFER);
    }
}
