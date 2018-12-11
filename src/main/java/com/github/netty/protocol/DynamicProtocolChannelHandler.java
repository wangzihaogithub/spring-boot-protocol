package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.ProtocolsRegister;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by acer01 on 2018/12/9/009.
 */
@ChannelHandler.Sharable
public class DynamicProtocolChannelHandler extends AbstractChannelHandler<ByteBuf,Object> {
    /**
     * 协议注册器列表
     */
    private List<ProtocolsRegister> protocolsRegisterList = new ProtocolsRegisterList();
    private Consumer<Channel> registerIntercept;

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
            if(channel.isRegistered()) {
                channel.pipeline().fireChannelRegistered();
            }
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

    public Consumer<Channel> getRegisterIntercept() {
        return registerIntercept;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        ctx.close();
    }

    /**
     * 协议注册列表
     */
    class ProtocolsRegisterList extends LinkedList<ProtocolsRegister>{
        @Override
        public void add(int index, ProtocolsRegister element) {
            logger.info("addProtocolsRegister({})",element.getProtocolName());
            super.add(index, element);
        }

        @Override
        public boolean add(ProtocolsRegister element) {
            logger.info("addProtocolsRegister({})",element.getProtocolName());
            return super.add(element);
        }

        @Override
        public boolean addAll(Collection<? extends ProtocolsRegister> c) {
            logger.info("addProtocolsRegister({})",String.join(",",c.stream().map(ProtocolsRegister::getProtocolName).collect(Collectors.toList())));
            return super.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends ProtocolsRegister> c) {
            logger.info("addProtocolsRegister({})",String.join(",",c.stream().map(ProtocolsRegister::getProtocolName).collect(Collectors.toList())));
            return super.addAll(index, c);
        }

        @Override
        public boolean remove(Object o) {
            logger.info("removeProtocolsRegister({})",o);
            return super.remove(o);
        }
    }
}