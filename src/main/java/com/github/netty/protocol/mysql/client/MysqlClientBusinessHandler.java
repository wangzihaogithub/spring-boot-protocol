package com.github.netty.protocol.mysql.client;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.MysqlPacket;
import com.github.netty.protocol.mysql.Session;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

@ChannelHandler.Sharable
public class MysqlClientBusinessHandler extends AbstractChannelHandler<ClientPacket,MysqlPacket> {
    private int maxPacketSize;
    private Session session;
    public MysqlClientBusinessHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ClientPacket msg) throws Exception {
        if (msg instanceof ClientHandshakePacket) {
            ctx.pipeline().replace(ClientConnectionDecoder.class,
                    "ClientCommandDecoder", new ClientCommandDecoder(getMaxPacketSize()));
        }
        onMysqlPacket(ctx, msg);
    }

    protected void onMysqlPacket(ChannelHandlerContext ctx, ClientPacket packet){

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
