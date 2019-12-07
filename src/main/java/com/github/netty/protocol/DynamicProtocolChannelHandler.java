package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.metrics.BytesMetricsChannelHandler;
import com.github.netty.metrics.MessageMetricsChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Created by wangzihao on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf,Object> {
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
    private int maxConnections;

    public DynamicProtocolChannelHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);

        ProtocolHandler protocolHandler = getProtocolHandler(msg);
        if(protocolHandler == null){
            onNoSupportProtocol(ctx,msg);
            return;
        }
        if(getTcpChannelCount() >= getMaxConnections()) {
            onOutOfMaxConnection(ctx, msg,new TcpChannel(channel, protocolHandler,this));
            return;
        }

        logger.debug("{} protocol bind to [{}]",channel, protocolHandler.getProtocolName());
        if(bytesMetricsChannelHandler != null){
            channel.pipeline().addFirst("bytemetrics", bytesMetricsChannelHandler);
        }
        if(messageMetricsChannelHandler != null){
            channel.pipeline().addLast("metrics", messageMetricsChannelHandler);
        }
        if(loggingHandler != null){
            channel.pipeline().addLast("logger", loggingHandler);
        }
        channel.pipeline().addLast("tcpChannel", new ChannelDuplexHandler(){
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                removeTcpChannel(ctx.channel().id());
            }
        });

        addTcpChannel(channel.id(),new TcpChannel(channel, protocolHandler,this));
        protocolHandler.addPipeline(channel);
        if (channel.isActive()) {
            channel.pipeline().fireChannelRegistered();
            channel.pipeline().fireChannelActive();
            channel.pipeline().fireChannelRead(msg);
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

    protected void onOutOfMaxConnection(ChannelHandlerContext ctx, ByteBuf msg,TcpChannel tcpChannel){
        ctx.close();
        if(msg.refCnt() > 0) {
            msg.release();
        }
    }

    protected void onNoSupportProtocol(ChannelHandlerContext ctx, ByteBuf msg){
        logger.warn("Received no support protocol. message=[{}]",msg.toString(StandardCharsets.UTF_8));
        ctx.close();
        if(msg.refCnt() > 0) {
            msg.release();
        }
    }

    protected void addTcpChannel(ChannelId id,TcpChannel tcpChannel){
        tcpChannel.attr(ATTR_KEY_TCP_CHANNEL).set(tcpChannel);
        TcpChannel.getChannels().put(id,tcpChannel);
    }

    protected void removeTcpChannel(ChannelId id){
        TcpChannel tcpChannel = TcpChannel.getChannels().remove(id);
        if(tcpChannel != null){
            tcpChannel.attr(ATTR_KEY_TCP_CHANNEL).set(null);
        }
    }

    protected int getTcpChannelCount(){
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

    public void enableTcpPackageLog(LogLevel logLevel) {
        this.loggingHandler = new LoggingHandler(getClass(), logLevel);
        this.messageMetricsChannelHandler = new MessageMetricsChannelHandler();
        this.bytesMetricsChannelHandler = new BytesMetricsChannelHandler();
    }

}