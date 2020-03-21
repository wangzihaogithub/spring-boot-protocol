package com.github.netty.protocol;

import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.SimpleNettyClient;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.mysql.MysqlProxyHandler;
import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.client.ClientConnectionDecoder;
import com.github.netty.protocol.mysql.client.MysqlClientBusinessHandler;
import com.github.netty.protocol.mysql.server.MysqlServerBusinessHandler;
import com.github.netty.protocol.mysql.server.ServerConnectionDecoder;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import com.github.netty.protocol.mysql.server.ServerPacketEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;

import java.net.InetSocketAddress;
import java.util.function.Supplier;


/**
 * Mysql Protocol Payload
 *
 * mysql client will not send the first packet, the server will, after receiving the link, immediately return the authentication information
 *
 * |-------------------------------------------------------------------------------
 * |Type	    |   Name	       |  Description
 * |-------------------------------------------------------------------------------
 * |int[3]	    |   payload_length |  Length of the payload. The number of bytes in the packet beyond the initial 4 bytes that make up the packet header.
 * |-------------------------------------------------------------------------------
 * |int[1]	    |   sequence_id	   |  Sequence ID
 * |-------------------------------------------------------------------------------
 * |string[var] |   payload	       |  [len=payload_length] payload of the packet
 * --------------------------------------------------------------------------------
 */
public class MysqlProtocol extends AbstractProtocol {
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private InetSocketAddress mysqlAddress;
    private int maxPacketSize;
    private final Supplier<MysqlServerBusinessHandler> serverBusinessHandler;
    private final Supplier<MysqlClientBusinessHandler> clientBusinessHandler;
    public MysqlProtocol(Supplier<MysqlServerBusinessHandler> serverBusinessHandler, Supplier<MysqlClientBusinessHandler> clientBusinessHandler) {
        this.serverBusinessHandler = serverBusinessHandler;
        this.clientBusinessHandler = clientBusinessHandler;
    }

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public String getProtocolName() {
        return "mysql";
    }

    @Override
    public boolean canSupport(Channel channel) {
        return true;
    }

    /**
     * mysql client will not send the first packet, the server will, after receiving the link, immediately return the authentication information
     * TODO: 2月24日 024 mysql canSupport impl
     * @param msg client first message
     * @return true=support, false=no support
     */
    @Override
    public boolean canSupport(ByteBuf msg) {
        return false;
//        msg.markReaderIndex();
//        try {
//            if (msg.isReadable(4)) {
//                final int packetSize = msg.readUnsignedMediumLE();
//                if (packetSize > maxPacketSize) {
//                    return false;
//                }
//                int sequenceId = msg.readByte();
//                if (!msg.isReadable(packetSize)) {
//                    return false;
//                }
//                if(!msg.isReadable(4)) {
//                    return false;
//                }
//                long vector = msg.readUnsignedIntLE();
//                EnumSet<CapabilityFlags> clientCapabilities = CodecUtils.toEnumSet(CapabilityFlags.class, vector);
//                if (!clientCapabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
//                    return false;
//                }
//                return true;
//            }
//            return false;
//        }finally {
//            msg.resetReaderIndex();
//        }
    }

    @Override
    public void addPipeline(Channel clientChannel) throws Exception {
        Session session = new Session();
        session.setClientChannel(clientChannel);

        SimpleNettyClient mysqlClient = new SimpleNettyClient("Mysql");
        mysqlClient.handlers(() -> {
                MysqlServerBusinessHandler serverBusinessHandler = this.serverBusinessHandler.get();
                serverBusinessHandler.setMaxPacketSize(maxPacketSize);
                serverBusinessHandler.setSession(session);
                return new ChannelHandler[]{
                        new MysqlProxyHandler(() -> clientChannel),
                        new ServerConnectionDecoder(maxPacketSize),
                        serverBusinessHandler};
            })
            .ioRatio(80)
            .ioThreadCount(1)
            .connect(mysqlAddress).get()
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    session.setServerChannel(future.channel());
                } else {
                    ServerErrorPacket errorPacket = new ServerErrorPacket(
                            0,2003,
                            "#HY000".getBytes(), future.cause().toString());
                    clientChannel.writeAndFlush(errorPacket);
                }
            });

        MysqlClientBusinessHandler clientBusinessHandler = this.clientBusinessHandler.get();
        clientBusinessHandler.setMaxPacketSize(maxPacketSize);
        clientBusinessHandler.setSession(session);
        clientChannel.pipeline().addLast(
                new MysqlProxyHandler(mysqlClient::getChannel),
                new ClientConnectionDecoder(maxPacketSize),
                new ServerPacketEncoder(),
                clientBusinessHandler);
    }

    public void setMysqlAddress(InetSocketAddress mysqlAddress) {
        this.mysqlAddress = mysqlAddress;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

}
