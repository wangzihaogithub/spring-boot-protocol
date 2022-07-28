package com.github.netty.protocol.mysql.server;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.Constants;
import com.github.netty.protocol.mysql.EventHandshakeSuccessful;
import com.github.netty.protocol.mysql.MysqlPacket;
import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.client.ClientHandshakePacket;
import com.github.netty.protocol.mysql.client.ClientQueryPacket;
import com.github.netty.protocol.mysql.listener.MysqlPacketListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.Collection;

/**
 * Here the user business logic
 * <p>
 * follows
 * 1. server to client {@link ServerHandshakePacket}
 * 2. client to server {@link ClientHandshakePacket}
 * 3. server to client {@link ServerOkPacket}
 * 4. client to server query... {@link ClientQueryPacket}
 * 5. server to client {@link ServerOkPacket}
 * 6. any....
 * <p>
 * Initial Handshake starts with server sending the `Initial Handshake Packet` {@link ServerHandshakePacket}.
 * After this, optionally,
 * client can request an SSL connection to be established with `SSL Connection Request Packet` TODO ,
 * and then client sends the `Handshake Response Packet` {@link ClientHandshakePacket}.
 */
public class MysqlBackendBusinessHandler extends AbstractChannelHandler<ServerPacket, MysqlPacket> {
    private int maxPacketSize;
    private Session session;
    private ServerHandshakePacket lastHandshakePacket;
    private Collection<MysqlPacketListener> mysqlPacketListeners;

    public MysqlBackendBusinessHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ServerPacket msg) throws Exception {
        if (msg instanceof ServerHandshakePacket) {
            this.lastHandshakePacket = (ServerHandshakePacket) msg;
            onHandshake(ctx, (ServerHandshakePacket) msg);
        } else if (this.lastHandshakePacket != null && msg instanceof ServerOkPacket) {
            EventHandshakeSuccessful eventHandshakeSuccessful = new EventHandshakeSuccessful(lastHandshakePacket, (ServerOkPacket) msg);
            session.getBackendChannel().pipeline().fireUserEventTriggered(eventHandshakeSuccessful);
            session.getFrontendChannel().pipeline().fireUserEventTriggered(eventHandshakeSuccessful);
            this.lastHandshakePacket = null;
        }
        if (mysqlPacketListeners != null && !mysqlPacketListeners.isEmpty()) {
            for (MysqlPacketListener mysqlPacketListener : mysqlPacketListeners) {
                try {
                    mysqlPacketListener.onMysqlPacket(msg, ctx, session, Constants.HANDLER_TYPE_BACKEND);
                } catch (Exception e) {
                    logger.warn("{} exception = {} ", mysqlPacketListener.toString(), e.toString(), e);
                }
            }
        }
        onMysqlPacket(ctx, msg);
    }

    @Override
    protected void onUserEventTriggered(ChannelHandlerContext ctx, Object evt) {
        super.onUserEventTriggered(ctx, evt);
        if (evt instanceof EventHandshakeSuccessful) {
            onHandshakeSuccessful(ctx, (EventHandshakeSuccessful) evt);
        }
    }

    protected void onHandshake(ChannelHandlerContext ctx, ServerHandshakePacket packet) {
        session.setBackendCapabilities(packet.getCapabilities());
        session.setServerCharsetAttr(packet.getCharacterSet());
        session.setConnectionId(packet.getConnectionId());
    }

    protected void onHandshakeSuccessful(ChannelHandlerContext ctx, EventHandshakeSuccessful event) {
        if (ctx.pipeline().context(ServerConnectionDecoder.class) != null) {
            ctx.pipeline().replace(ServerConnectionDecoder.class,
                    "ServerResultsetDecoder", new ServerResultsetDecoder(session, getMaxPacketSize()));
        }
    }

    protected void onMysqlPacket(ChannelHandlerContext ctx, ServerPacket packet) {

    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Collection<MysqlPacketListener> getMysqlPacketListeners() {
        return mysqlPacketListeners;
    }

    public void setMysqlPacketListeners(Collection<MysqlPacketListener> mysqlPacketListeners) {
        this.mysqlPacketListeners = mysqlPacketListeners;
    }
}
