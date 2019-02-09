package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolsRegister;
import com.github.netty.metrics.BytesMetricsChannelHandler;
import com.github.netty.metrics.MessageMetricsChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Created by acer01 on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf,Object> {
    /**
     * 协议注册器列表, 动态协议会找一个合适的协议注册到新链接上
     */
    private Collection<ProtocolsRegister> protocolsRegisters;
    /**
     * 通信监控 (读写/次)
     */
    private MessageMetricsChannelHandler messageMetricsChannelHandler;
    /**
     * 数据包监控 (读写/字节)
     */
    private BytesMetricsChannelHandler bytesMetricsChannelHandler;
    /**
     * 日志打印
     */
    private LoggingHandler loggingHandler;

    public DynamicProtocolChannelHandler(Collection<ProtocolsRegister> protocolsRegisters, boolean enableTcpPackageLog) {
        super(false);
        this.protocolsRegisters = protocolsRegisters;
        if(enableTcpPackageLog) {
            this.loggingHandler = new LoggingHandler(getClass(), LogLevel.INFO);
            this.messageMetricsChannelHandler = new MessageMetricsChannelHandler();
            this.bytesMetricsChannelHandler = new BytesMetricsChannelHandler();
        }
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);
        for(ProtocolsRegister protocolsRegister : protocolsRegisters){
            if(!protocolsRegister.canSupport(msg)) {
                continue;
            }
            logger.info("Channel protocols register by [{}]",protocolsRegister.getProtocolName());

            if(bytesMetricsChannelHandler != null){
                channel.pipeline().addFirst("bytemetrics", bytesMetricsChannelHandler);
            }
            if(messageMetricsChannelHandler != null){
                channel.pipeline().addLast("metrics", messageMetricsChannelHandler);
            }
            if(loggingHandler != null){
                channel.pipeline().addLast("logger", loggingHandler);
            }

            protocolsRegister.register(channel);
            if(channel.isRegistered()) {
                channel.pipeline().fireChannelRegistered();
            }
            if (channel.isActive()) {
                channel.pipeline().fireChannelActive();
                channel.pipeline().fireChannelRead(msg);
            }
            return;
        }

        logger.warn("Received no support protocols. message=[{}]",msg.toString(StandardCharsets.UTF_8));
        if(msg.refCnt() > 0) {
            msg.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        ctx.close();
    }

}