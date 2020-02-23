package com.github.netty.protocol.mysql.server;

import com.github.netty.protocol.mysql.Session;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ServerToClientHandler extends ChannelInboundHandlerAdapter {
    private final Session session;
    public ServerToClientHandler(Session session) {
        this.session = session;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ServerErrorPacket errorPacket = new ServerErrorPacket(
                0,3000,"#HY000".getBytes(),cause.toString());
        session.getClientChannel().writeAndFlush(errorPacket);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        session.getClientChannel().write(msg);
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        session.getClientChannel().writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

}
