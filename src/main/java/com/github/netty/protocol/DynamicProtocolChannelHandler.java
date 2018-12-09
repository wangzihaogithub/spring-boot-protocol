package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolsRegister;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by acer01 on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf> {
    /**
     * 协议注册器列表
     */
    private List<ProtocolsRegister> protocolsRegisterList = new LinkedList<>();
    public DynamicProtocolChannelHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);
        for(ProtocolsRegister protocolsRegister : protocolsRegisterList){
            if(protocolsRegister.canSupport(msg)){
                logger.info("Channel protocols register by [{0}]",protocolsRegister.getProtocolName());
                protocolsRegister.register(channel);
                channel.pipeline().fireChannelRead(msg);
                return;
            }
        }
        logger.warn("Received no support protocols. message=[{0}]",msg.toString(Charset.forName("UTF-8")));
    }

    public List<ProtocolsRegister> getProtocolsRegisterList() {
        return protocolsRegisterList;
    }
}