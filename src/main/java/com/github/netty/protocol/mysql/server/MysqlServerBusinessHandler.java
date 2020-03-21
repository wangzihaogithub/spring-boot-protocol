package com.github.netty.protocol.mysql.server;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.MysqlPacket;
import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.client.ClientHandshakePacket;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

@ChannelHandler.Sharable
public class MysqlServerBusinessHandler extends AbstractChannelHandler<ServerPacket,MysqlPacket> {
    private int maxPacketSize;
    private Session session;
    public MysqlServerBusinessHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ServerPacket msg) throws Exception {
        if (msg instanceof ClientHandshakePacket) {
            ctx.pipeline().replace(ServerConnectionDecoder.class,
                    "ServerResultsetDecoder", new ServerResultsetDecoder(getMaxPacketSize()));
        }
        onMysqlPacket(ctx, msg);
    }

    protected void onMysqlPacket(ChannelHandlerContext ctx, ServerPacket packet){

    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }
}
