package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ClientToServerHandler extends ChannelInboundHandlerAdapter {
    private final Session session;
    public ClientToServerHandler(Session session) {
        this.session = session;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ServerErrorPacket errorPacket = new ServerErrorPacket(
                0,3000,"#HY000".getBytes(),cause.toString());
        ctx.channel().writeAndFlush(errorPacket);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(!(msg instanceof ByteBuf)){
            ctx.fireChannelRead(msg);
            return;
        }
        ByteBuf msgByteBuf = (ByteBuf) msg;
        // directly write getClientChannel data to getMysqlChannel real mysql connection
        ByteBuf byteBuf = msgByteBuf.alloc().heapBuffer(msgByteBuf.readableBytes());
        msgByteBuf.getBytes(0,byteBuf);
        ctx.fireChannelRead(byteBuf);
        session.getMysqlChannel().write(msgByteBuf);
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        session.getMysqlChannel().writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

}
