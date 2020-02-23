package com.github.netty.protocol;

import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.mysql.CapabilityFlags;
import com.github.netty.protocol.mysql.CodecUtils;
import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.client.ClientBusinessHandler;
import com.github.netty.protocol.mysql.client.ClientConnectionDecoder;
import com.github.netty.protocol.mysql.client.ClientToServerHandler;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import com.github.netty.protocol.mysql.server.ServerPacketEncoder;
import com.github.netty.protocol.mysql.server.ServerToClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;
import java.util.EnumSet;


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
public class MysqlServerProtocol extends AbstractProtocol {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private InetSocketAddress mysqlAddress;
    private int maxPacketSize;

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public String getProtocolName() {
        return "mysql";
    }

    /**
     * mysql client will not send the first packet, the server will, after receiving the link, immediately return the authentication information
     * TODO: 2月24日 024 mysql canSupport impl
     * @param msg client first message
     * @return true=support, false=no support
     */
    @Override
    public boolean canSupport(ByteBuf msg) {
        msg.markReaderIndex();
        try {
            if (msg.isReadable(4)) {
                final int packetSize = msg.readUnsignedMediumLE();
                if (packetSize > maxPacketSize) {
                    return false;
                }
                int sequenceId = msg.readByte();
                if (!msg.isReadable(packetSize)) {
                    return false;
                }
                if(!msg.isReadable(4)) {
                    return false;
                }
                long vector = msg.readUnsignedIntLE();
                EnumSet<CapabilityFlags> clientCapabilities = CodecUtils.toEnumSet(CapabilityFlags.class, vector);
                if (!clientCapabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
                    return false;
                }
                return true;
            }
            return false;
        }finally {
            msg.resetReaderIndex();
        }
    }

    @Override
    public void addPipeline(Channel clientChannel) throws Exception {
        Session session = new Session();
        session.setClientChannel(clientChannel);
        connect(mysqlAddress, new ServerToClientHandler(session))
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel mysqlChannel = future.channel();
                        logger.info("on channel connect future operationComplete,  : [{}], getMysqlChannel : [{}]",clientChannel, mysqlChannel);
                        session.setMysqlChannel(mysqlChannel);
                    } else {
                        ServerErrorPacket errorPacket = new ServerErrorPacket(
                                0,2003,
                                "#HY000".getBytes(), future.cause().toString());
                        clientChannel.writeAndFlush(errorPacket);
                    }
                });
        clientChannel.pipeline().addLast(
                new ClientToServerHandler(session),
                new ClientConnectionDecoder(maxPacketSize),
                new ServerPacketEncoder(),
                new ClientBusinessHandler());
    }

    public ChannelFuture connect(InetSocketAddress address, ChannelHandler... channelHandler) {
        return new Bootstrap().group(new NioEventLoopGroup(0,new DefaultThreadFactory("workGroup")))
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(channelHandler);
                    }
                }).connect(address);
    }

    public void setMysqlAddress(InetSocketAddress mysqlAddress) {
        this.mysqlAddress = mysqlAddress;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

}
