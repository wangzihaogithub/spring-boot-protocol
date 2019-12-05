package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.TcpChannel;
import com.github.netty.core.TcpEvent;
import com.github.netty.metrics.BytesMetricsChannelHandler;
import com.github.netty.metrics.MessageMetricsChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Created by wangzihao on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf,Object> {
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

    public DynamicProtocolChannelHandler(Collection<ProtocolHandler> protocolHandlers, boolean enableTcpPackageLog, LogLevel logLevel, int maxConnections) {
        super(false);
        this.protocolHandlers = protocolHandlers;
        if(enableTcpPackageLog) {
            this.loggingHandler = new LoggingHandler(getClass(), logLevel);
            this.messageMetricsChannelHandler = new MessageMetricsChannelHandler();
            this.bytesMetricsChannelHandler = new BytesMetricsChannelHandler();
        }
        this.maxConnections = maxConnections;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);
        for(ProtocolHandler protocolHandler : protocolHandlers){
            if(!protocolHandler.canSupport(msg)) {
                continue;
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
            TcpChannel.getChannels().put(channel.id(),new TcpChannel(channel,protocolHandler));
            channel.pipeline().addLast("channels", new ChannelDuplexHandler(){
                @Override
                public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                    TcpChannel.getChannels().remove(ctx.channel().id());
                    super.close(ctx,promise);
                }
            });
            protocolHandler.addPipeline(channel);
            if(channel.isRegistered()) {
                channel.pipeline().fireChannelRegistered();
            }
            if (channel.isActive()) {
                channel.pipeline().fireChannelActive();
                channel.pipeline().fireChannelRead(msg);
            }
            return;
        }

        logger.warn("Received no support protocol. message=[{}]",msg.toString(StandardCharsets.UTF_8));
        if(msg.refCnt() > 0) {
            msg.release();
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if(TcpChannel.getChannels().size() > maxConnections){
            ctx.fireUserEventTriggered(new TcpEvent(TcpEvent.EVENT_CONNECTION_REFUSED,ctx.channel()));
            ctx.writeAndFlush("refused connect")
                    .addListener((ChannelFutureListener) future -> {
                        TcpChannel.getChannels().remove(ctx.channel().id());
                        future.channel().close();
                    });
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        TcpChannel.getChannels().remove(ctx.channel().id());
        ctx.close();
    }

}