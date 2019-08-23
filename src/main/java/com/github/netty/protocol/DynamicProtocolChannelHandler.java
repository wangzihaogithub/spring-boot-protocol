package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.metrics.BytesMetricsChannelHandler;
import com.github.netty.metrics.MessageMetricsChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.Semaphore;

/**
 * Created by wangzihao on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf,Object> {
    public static final AttributeKey<Boolean> CONNECTION_OVERLOAD_ATTR = AttributeKey.valueOf("connectionOverload");
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
    private Semaphore maxConnectionSemaphore;
    private int maxConnections;

    public DynamicProtocolChannelHandler(Collection<ProtocolHandler> protocolHandlers, boolean enableTcpPackageLog, LogLevel logLevel,int maxConnections) {
        super(false);
        this.protocolHandlers = protocolHandlers;
        if(enableTcpPackageLog) {
            this.loggingHandler = new LoggingHandler(getClass(), logLevel);
            this.messageMetricsChannelHandler = new MessageMetricsChannelHandler();
            this.bytesMetricsChannelHandler = new BytesMetricsChannelHandler();
        }
        this.maxConnections = maxConnections;
        this.maxConnectionSemaphore = new Semaphore(maxConnections,false);
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
        if(!maxConnectionSemaphore.tryAcquire()){
            ctx.channel().attr(CONNECTION_OVERLOAD_ATTR).set(Boolean.TRUE);
            logger.warn("Connection overload! maxConnections={},availablePermits={}, threads waiting to acquire number={} ",
                    maxConnections,maxConnectionSemaphore.availablePermits(),maxConnectionSemaphore.getQueueLength());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        ctx.close();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        Channel channel = ctx.channel();
        // connectionOverload
        if(channel.hasAttr(CONNECTION_OVERLOAD_ATTR) && channel.attr(CONNECTION_OVERLOAD_ATTR).get()){
            return;
        }
        maxConnectionSemaphore.release();
    }

}