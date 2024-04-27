package com.github.netty.protocol;

import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.mysql.Constants;
import com.github.netty.protocol.mysql.MysqlProxyHandler;
import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.client.ClientConnectionDecoder;
import com.github.netty.protocol.mysql.client.ClientPacketEncoder;
import com.github.netty.protocol.mysql.client.MysqlFrontendBusinessHandler;
import com.github.netty.protocol.mysql.exception.ProxyException;
import com.github.netty.protocol.mysql.listener.MysqlPacketListener;
import com.github.netty.protocol.mysql.server.MysqlBackendBusinessHandler;
import com.github.netty.protocol.mysql.server.ServerConnectionDecoder;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import com.github.netty.protocol.mysql.server.ServerPacketEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;


/**
 * Mysql Protocol Payload
 * <p>
 * mysql client will not send the first packet, the server will, after receiving the link, immediately return the authentication information
 * <p>
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
    protected final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final List<MysqlPacketListener> mysqlPacketListeners = new CopyOnWriteArrayList<>();
    private InetSocketAddress mysqlAddress;
    private int maxPacketSize = Constants.DEFAULT_MAX_PACKET_SIZE;
    private Supplier<MysqlBackendBusinessHandler> backendBusinessHandler = MysqlBackendBusinessHandler::new;
    private Supplier<MysqlFrontendBusinessHandler> frontendBusinessHandler = MysqlFrontendBusinessHandler::new;

    public MysqlProtocol() {
    }

    public MysqlProtocol(InetSocketAddress mysqlAddress) {
        this.mysqlAddress = mysqlAddress;
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
     *
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

    protected String newSessionId(InetSocketAddress frontendAddress, InetSocketAddress backendAddress) {
        String backendId = backendAddress.getHostString() + "_" + backendAddress.getPort();
        String frontendId = frontendAddress.getHostString() + "_" + frontendAddress.getPort();
        return backendId + "-" + frontendId;
    }

    @Override
    public void addPipeline(Channel frontendChannel, ByteBuf clientFirstMsg) throws Exception {
        Session session = new Session(newSessionId((InetSocketAddress) frontendChannel.remoteAddress(), mysqlAddress));
        session.setFrontendChannel(frontendChannel);

        SimpleNettyClient mysqlClient = new SimpleNettyClient("Mysql");
        mysqlClient.handlers(() -> {
            MysqlBackendBusinessHandler backendBusinessHandler = this.backendBusinessHandler.get();
            backendBusinessHandler.setMysqlPacketListeners(mysqlPacketListeners);
            backendBusinessHandler.setMaxPacketSize(maxPacketSize);
            backendBusinessHandler.setSession(session);
            return new ChannelHandler[]{
                    new MysqlProxyHandler(session::getFrontendChannel),
                    new ServerConnectionDecoder(session, maxPacketSize),
                    new ClientPacketEncoder(session),
                    new ServerPacketEncoder(session),
                    backendBusinessHandler};
        })
                .ioRatio(80)
                .ioThreadCount(1)
                .connect(mysqlAddress).get()
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        session.setBackendChannel(future.channel());
                    } else {
                        String stackTrace = ProxyException.stackTraceToString(future.cause());
                        ServerErrorPacket errorPacket = new ServerErrorPacket(
                                0, ProxyException.ERROR_BACKEND_CONNECT_FAIL,
                                "#HY000".getBytes(), stackTrace);
                        frontendChannel.writeAndFlush(errorPacket).addListener(ChannelFutureListener.CLOSE);
                    }
                });

        MysqlFrontendBusinessHandler frontendBusinessHandler = this.frontendBusinessHandler.get();
        frontendBusinessHandler.setMaxPacketSize(maxPacketSize);
        frontendBusinessHandler.setSession(session);
        frontendBusinessHandler.setMysqlPacketListeners(mysqlPacketListeners);
        frontendChannel.pipeline().addLast(
                new MysqlProxyHandler(newBackendChannelSupplier(session)),
                new ClientConnectionDecoder(session, maxPacketSize),
                new ClientPacketEncoder(session),
                new ServerPacketEncoder(session),
                frontendBusinessHandler);
    }

    protected Supplier<Channel> newBackendChannelSupplier(Session session) {
        return () -> {
            Channel backendChannel = session.getBackendChannel();
            if (backendChannel == null) {
                throw new ProxyException(ProxyException.ERROR_BACKEND_NO_CONNECTION, "cannot find a backendChannel");
            }
            return backendChannel;
        };
    }

    public void setBackendBusinessHandler(Supplier<MysqlBackendBusinessHandler> backendBusinessHandler) {
        this.backendBusinessHandler = backendBusinessHandler;
    }

    public void setFrontendBusinessHandler(Supplier<MysqlFrontendBusinessHandler> frontendBusinessHandler) {
        this.frontendBusinessHandler = frontendBusinessHandler;
    }

    public InetSocketAddress getMysqlAddress() {
        return mysqlAddress;
    }

    public void setMysqlAddress(InetSocketAddress mysqlAddress) {
        this.mysqlAddress = mysqlAddress;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public List<MysqlPacketListener> getMysqlPacketListeners() {
        return mysqlPacketListeners;
    }

    public static class SimpleNettyClient extends AbstractNettyClient {
        private ChannelHandler handler;

        public SimpleNettyClient(String namePre) {
            super(namePre, null);
        }

        @Override
        protected ChannelHandler newBossChannelHandler() {
            return handler;
        }

        public SimpleNettyClient handler(ChannelHandler handler) {
            this.handler = handler;
            return this;
        }

        public SimpleNettyClient handlers(Supplier<ChannelHandler[]> supplier) {
            this.handler = new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(supplier.get());
                }
            };
            return this;
        }

        public SimpleNettyClient ioThreadCount(int ioThreadCount) {
            setIoThreadCount(ioThreadCount);
            return this;
        }

        public SimpleNettyClient ioRatio(int ioRatio) {
            setIoRatio(ioRatio);
            return this;
        }

        public SimpleNettyClient remoteAddress(InetSocketAddress remoteAddress) {
            super.remoteAddress = remoteAddress;
            return this;
        }

    }

}
