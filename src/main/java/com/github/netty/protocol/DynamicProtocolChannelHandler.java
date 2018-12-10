package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolsRegister;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by acer01 on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf,Object> {
    /**
     * 协议注册器列表
     */
    private List<ProtocolsRegister> protocolsRegisterList = new LinkedList<>();
    private Consumer<Channel> registerIntercept;

    public DynamicProtocolChannelHandler() {
        this(null);
    }

    public DynamicProtocolChannelHandler(Consumer<Channel> registerIntercept) {
        super(false);
        this.registerIntercept = registerIntercept;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        channel.pipeline().remove(this);
        for(ProtocolsRegister protocolsRegister : protocolsRegisterList){
            if(!protocolsRegister.canSupport(msg)) {
                continue;
            }

            if(registerIntercept != null) {
                registerIntercept.accept(channel);
            }

            logger.info("Channel protocols register by [{}]",protocolsRegister.getProtocolName());
            protocolsRegister.register(channel);
            channel.pipeline().fireChannelRegistered();
            if (channel.isActive()) {
                channel.pipeline().fireChannelActive();
                channel.pipeline().fireChannelRead(msg);
            }
            return;
        }

        logger.warn("Received no support protocols. message=[{}]",msg.toString(Charset.forName("UTF-8")));
        if(msg.refCnt() > 0) {
            ReferenceCountUtil.release(msg);
        }
    }

    public List<ProtocolsRegister> getProtocolsRegisterList() {
        return protocolsRegisterList;
    }

    public void setRegisterIntercept(Consumer<Channel> registerIntercept) {
        this.registerIntercept = registerIntercept;
    }

    public Consumer<Channel> getRegisterIntercept() {
        return registerIntercept;
    }
}