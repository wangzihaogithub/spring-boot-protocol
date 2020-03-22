package com.github.netty.protocol.mysql;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.util.function.Supplier;

public class MysqlProxyHandler extends AbstractChannelHandler<ByteBuf,ByteBuf> {
    private static final AttributeKey<ByteBuf> READY_WRITE_PACKET_ATTR = AttributeKey.valueOf(MysqlProxyHandler.class+"#ByteBuf");
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
        // directly write getFrontendChannel data to getMysqlChannel real mysql connection
        ByteBuf userByteBuf = ctx.alloc().heapBuffer(msg.readableBytes());
        msg.getBytes(0,userByteBuf);

        Channel channel = channelSupplier.get();
        setReadyWritePacket(channel,msg);
        ctx.fireChannelRead(userByteBuf);
        ByteBuf readyWritePacket = getReadyWritePacket(channel);
        if(readyWritePacket != null) {
            channel.write(readyWritePacket);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        channelSupplier.get().writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    public static void setReadyWritePacket(Channel channel, ByteBuf byteBuf){
        channel.attr(READY_WRITE_PACKET_ATTR).set(byteBuf);
    }

    public static ByteBuf getReadyWritePacket(Channel channel){
        return channel.attr(READY_WRITE_PACKET_ATTR).get();
    }
}
