package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.TcpChannel;
import com.github.netty.core.util.BytesMetricsChannelHandler;
import com.github.netty.core.util.MessageMetricsChannelHandler;
import io.netty.buffer.ByteBuf;
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
    public static final AttributeKey<TcpChannel> ATTR_KEY_TCP_CHANNEL = AttributeKey.valueOf(TcpChannel.class+"#Dy");
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
    private int maxConnections = 10000;
    /**
     * The timeout (milliseconds) of the first client package.
     * When there is a new link Access, if the packet is confiscated in time,
     * the server will turn off the link or perform timeout processing.
     */
    private long firstClientPacketReadTimeoutMs = 1000;

    public DynamicProtocolChannelHandler() {
        super(false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Channel channel = ctx.channel();
        ChannelId id = channel.id();

        ctx.executor().schedule(()-> {
            TcpChannel tcpChannel = getTcpChannel(id);
            if(tcpChannel == null ||
                    (tcpChannel.getProtocol() == null && tcpChannel.isActive())) {
                onProtocolBindTimeout(ctx);
            }
        },firstClientPacketReadTimeoutMs, TimeUnit.MILLISECONDS);

        channel.pipeline().addLast("tcpChannel", new ChannelDuplexHandler() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                removeTcpChannel(ctx.channel().id());
            }
        });
        if(bytesMetricsChannelHandler != null){
            channel.pipeline().addFirst("bytemetrics", bytesMetricsChannelHandler);
        }
        if(messageMetricsChannelHandler != null){
            channel.pipeline().addLast("metrics", messageMetricsChannelHandler);
        }
        if(loggingHandler != null){
            channel.pipeline().addLast("logger", loggingHandler);
        }
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);

        ProtocolHandler protocolHandler = getProtocolHandler(msg);
        if(protocolHandler == null){
            addTcpChannel(channel.id(),new TcpChannel(channel, null,this));
            onNoSupportProtocol(ctx,msg);
            return;
        }
        if(getTcpChannelCount() >= getMaxConnections()) {
            TcpChannel tcpChannel = new TcpChannel(channel, protocolHandler, this);
            addTcpChannel(channel.id(),tcpChannel);
            onOutOfMaxConnection(ctx, msg,tcpChannel);
            return;
        }

        addPipeline(ctx,protocolHandler);
        if(channel.isActive()){
            channel.pipeline().fireChannelRead(msg);
        }
    }

    protected void addPipeline(ChannelHandlerContext ctx, ProtocolHandler protocolHandler) throws Exception {
        Channel channel = ctx.channel();
        logger.debug("{} protocol bind to [{}]",channel, protocolHandler.getProtocolName());

        addTcpChannel(channel.id(),new TcpChannel(channel, protocolHandler,this));
        protocolHandler.addPipeline(channel);
        if (channel.isRegistered()) {
            channel.pipeline().fireChannelRegistered();
        }
        if(channel.isActive()) {
            channel.pipeline().fireChannelActive();
        }
    }

    public ProtocolHandler getProtocolHandler(ByteBuf msg){
        for(ProtocolHandler protocolHandler : protocolHandlers) {
            if (protocolHandler.canSupport(msg)) {
                return protocolHandler;
            }
        }
        return null;
    }

    public ProtocolHandler getProtocolHandler(Channel channel){
        for(ProtocolHandler protocolHandler : protocolHandlers) {
            if (protocolHandler.canSupport(channel)) {
                return protocolHandler;
            }
        }
        return null;
    }

    protected void onOutOfMaxConnection(ChannelHandlerContext ctx, ByteBuf msg, TcpChannel tcpChannel){
        ctx.close();
        if(msg != null && msg.refCnt() > 0) {
            msg.release();
        }
    }

    protected void onProtocolBindTimeout(ChannelHandlerContext ctx){
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);

        ProtocolHandler protocolHandler = getProtocolHandler(channel);
        if(protocolHandler == null) {
            addTcpChannel(channel.id(),new TcpChannel(channel, null,this));
            onNoSupportProtocol(ctx,null);
            return;
        }

        if(getTcpChannelCount() >= getMaxConnections()) {
            TcpChannel tcpChannel = new TcpChannel(channel, protocolHandler, this);
            addTcpChannel(channel.id(),tcpChannel);
            onOutOfMaxConnection(ctx, null,tcpChannel);
            return;
        }

        try {
            addPipeline(ctx,protocolHandler);
        } catch (Exception e) {
            ctx.fireExceptionCaught(e);
        }
    }

    protected void onNoSupportProtocol(ChannelHandlerContext ctx, ByteBuf msg){
        if(msg != null) {
            logger.warn("Received no support protocol. message=[{}]", msg.toString(Charset.forName("UTF-8")));
            if (msg.refCnt() > 0) {
                msg.release();
            }
        }
        ctx.close();
    }

    public TcpChannel getTcpChannel(ChannelId id){
        return TcpChannel.getChannels().get(id);
    }

    public void addTcpChannel(ChannelId id, TcpChannel tcpChannel){
        tcpChannel.attr(ATTR_KEY_TCP_CHANNEL).set(tcpChannel);
        TcpChannel.getChannels().put(id,tcpChannel);
    }

    public void removeTcpChannel(ChannelId id){
        TcpChannel tcpChannel = TcpChannel.getChannels().remove(id);
        if(tcpChannel != null){
            tcpChannel.attr(ATTR_KEY_TCP_CHANNEL).set(null);
        }
    }

    public int getTcpChannelCount(){
        return TcpChannel.getChannels().size();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        ctx.close();
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxConnections() {
        return maxConnections;
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

}