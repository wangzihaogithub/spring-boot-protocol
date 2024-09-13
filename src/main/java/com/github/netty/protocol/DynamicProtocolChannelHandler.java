package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.TcpChannel;
import com.github.netty.core.util.BytesMetricsChannelHandler;
import com.github.netty.core.util.MessageMetricsChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Created by wangzihao on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf, Object> {
    public static final AttributeKey<TcpChannel> ATTR_KEY_TCP_CHANNEL = AttributeKey.valueOf(TcpChannel.class + "#Dy");
    private final RemoveTcpChannelHandler removeTcpChannelHandler = new RemoveTcpChannelHandler();
    /**
     * Protocol registry list, dynamic protocol will find a suitable protocol to supportPipeline on the new link
     */
    private Collection<ProtocolHandler> protocolHandlers;
    /**
     * Communication monitoring (read write/time)
     */
    private MessageMetricsChannelHandler messageMetricsChannelHandler;
    /**
     * Packet monitoring (read write/byte)
     */
    private BytesMetricsChannelHandler bytesMetricsChannelHandler;
    /**
     * Log print
     */
    private LoggingHandler loggingHandler;
    /**
     * maxConnections
     */
    private int maxConnections = 1000000;
    /**
     * The timeout (milliseconds) of the first client package.
     * When there is a new link Access, if the packet is confiscated in time,
     * the server will turn off the link or perform timeout processing.
     */
    private long firstClientPacketReadTimeoutMs = 800;

    public DynamicProtocolChannelHandler() {
        super(false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Channel channel = ctx.channel();
        ChannelId id = channel.id();

        TcpChannel tcpChannel = new TcpChannel(channel, this);
        addConnection(id, tcpChannel);

        if (protocolHandlers.size() > 1 && firstClientPacketReadTimeoutMs >= 0) {
            ctx.executor().schedule(() -> {
                if (tcpChannel.getProtocol() == null && tcpChannel.isActive()) {
                    onProtocolBindTimeout(ctx, tcpChannel);
                }
            }, firstClientPacketReadTimeoutMs, TimeUnit.MILLISECONDS);
        }

        channel.pipeline().addLast(removeTcpChannelHandler);
        if (bytesMetricsChannelHandler != null) {
            channel.pipeline().addFirst("bytemetrics", bytesMetricsChannelHandler);
        }
        if (messageMetricsChannelHandler != null) {
            channel.pipeline().addLast("metrics", messageMetricsChannelHandler);
        }
        if (loggingHandler != null) {
            channel.pipeline().addLast("logger", loggingHandler);
        }
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf clientFirstMsg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);

        ProtocolHandler protocolHandler = getProtocolHandler(clientFirstMsg);
        int currentConnections = getConnectionCount();
        int maxConnections = getMaxConnections();
        if (currentConnections > maxConnections) {
            TcpChannel tcpChannel = getConnection(channel.id());
            tcpChannel.setProtocol(protocolHandler);
            if (!onOutOfMaxConnection(ctx, clientFirstMsg, tcpChannel, currentConnections, maxConnections)) {
                if (clientFirstMsg.refCnt() > 0) {
                    clientFirstMsg.release();
                }
                return;
            }
        }
        if (protocolHandler == null) {
            onNoSupportProtocol(ctx, clientFirstMsg);
            return;
        }
        TcpChannel tcpChannel = getConnection(channel.id());
        tcpChannel.setProtocol(protocolHandler);
        addPipeline(ctx, protocolHandler, clientFirstMsg);
        if (channel.isActive()) {
            channel.pipeline().fireChannelRead(clientFirstMsg);
        }
    }

    protected void addPipeline(ChannelHandlerContext ctx, ProtocolHandler protocolHandler, ByteBuf clientFirstMsg) throws Exception {
        Channel channel = ctx.channel();
        if (logger.isDebugEnabled()) {
            logger.debug("{} protocol bind to [{}]", channel, protocolHandler.getProtocolName());
        }

        protocolHandler.addPipeline(channel, clientFirstMsg);
        if (channel.isRegistered()) {
            channel.pipeline().fireChannelRegistered();
        }
        if (channel.isActive()) {
            channel.pipeline().fireChannelActive();
        }
    }

    public ProtocolHandler getProtocolHandler(ByteBuf clientFirstMsg) {
        if (protocolHandlers.size() == 1) {
            return protocolHandlers.iterator().next();
        }
        for (ProtocolHandler protocolHandler : protocolHandlers) {
            if (protocolHandler.canSupport(clientFirstMsg)) {
                return protocolHandler;
            }
        }
        return null;
    }

    public ProtocolHandler getProtocolHandler(Channel channel) {
        if (protocolHandlers.size() == 1) {
            return protocolHandlers.iterator().next();
        }
        for (ProtocolHandler protocolHandler : protocolHandlers) {
            if (protocolHandler.canSupport(channel)) {
                return protocolHandler;
            }
        }
        return null;
    }

    protected boolean onOutOfMaxConnection(ChannelHandlerContext ctx, ByteBuf clientFirstMsg,
                                           TcpChannel tcpChannel,
                                           int currentConnections,
                                           int maxConnections) {
        ProtocolHandler protocolHandler = tcpChannel.getProtocol();
        if (protocolHandler != null) {
            return protocolHandler.onOutOfMaxConnection(clientFirstMsg, tcpChannel, currentConnections, maxConnections);
        }
        return false;
    }

    protected void onProtocolBindTimeout(ChannelHandlerContext ctx, TcpChannel tcpChannel) {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);
        ByteBuf clientFirstMsg = Unpooled.EMPTY_BUFFER;

        ProtocolHandler protocolHandler = getProtocolHandler(channel);
        if (protocolHandler == null) {
            onNoSupportProtocol(ctx, null);
            return;
        }

        int currentConnections = getConnectionCount();
        int maxConnections = getMaxConnections();
        if (currentConnections > maxConnections) {
            tcpChannel.setProtocol(protocolHandler);
            if (!onOutOfMaxConnection(ctx, clientFirstMsg, tcpChannel, currentConnections, maxConnections)) {
                return;
            }
        }

        try {
            addPipeline(ctx, protocolHandler, clientFirstMsg);
        } catch (Exception e) {
            ctx.fireExceptionCaught(e);
        }
    }

    protected void onNoSupportProtocol(ChannelHandlerContext ctx, ByteBuf clientFirstMsg) {
        if (clientFirstMsg != null) {
            if (logger.isWarnEnabled()) {
                logger.warn("Received no support protocol. message=[{}]", clientFirstMsg.toString(Charset.forName("UTF-8")));
            }
            if (clientFirstMsg.refCnt() > 0) {
                clientFirstMsg.release();
            }
        }
        ctx.close();
    }

    public TcpChannel getConnection(ChannelId id) {
        return TcpChannel.getChannels().get(id);
    }

    public void addConnection(ChannelId id, TcpChannel tcpChannel) {
        tcpChannel.attr(ATTR_KEY_TCP_CHANNEL).set(tcpChannel);
        TcpChannel.getChannels().put(id, tcpChannel);
    }

    public void removeConnection(ChannelId id) {
        TcpChannel tcpChannel = TcpChannel.getChannels().remove(id);
        if (tcpChannel != null) {
            tcpChannel.attr(ATTR_KEY_TCP_CHANNEL).set(null);
        }
    }

    public int getConnectionCount() {
        return TcpChannel.getChannels().size();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isWarnEnabled()) {
            logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        }
        ctx.close();
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public void setProtocolHandlers(Collection<ProtocolHandler> protocolHandlers) {
        this.protocolHandlers = protocolHandlers;
    }

    public long getFirstClientPacketReadTimeoutMs() {
        return firstClientPacketReadTimeoutMs;
    }

    public void setFirstClientPacketReadTimeoutMs(long firstClientPacketReadTimeoutMs) {
        this.firstClientPacketReadTimeoutMs = firstClientPacketReadTimeoutMs;
    }

    public void enableTcpPackageLog(LogLevel logLevel) {
        this.loggingHandler = new LoggingHandler(getClass(), logLevel);
        this.messageMetricsChannelHandler = new MessageMetricsChannelHandler();
        this.bytesMetricsChannelHandler = new BytesMetricsChannelHandler();
    }

    @ChannelHandler.Sharable
    public class RemoveTcpChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            try {
                super.channelInactive(ctx);
            } finally {
                removeConnection(ctx.channel().id());
            }
        }
    }

}