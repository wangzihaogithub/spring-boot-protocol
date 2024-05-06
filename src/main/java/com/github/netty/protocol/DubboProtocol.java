package com.github.netty.protocol;

import com.github.netty.core.AbstractProtocol;
import com.github.netty.protocol.dubbo.DubboDecoder;
import com.github.netty.protocol.dubbo.ProxyFrontendHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.util.function.Supplier;

public class DubboProtocol extends AbstractProtocol {
    private Supplier<ProxyFrontendHandler> proxySupplier;

    public DubboProtocol() {
    }

    public DubboProtocol(Supplier<ProxyFrontendHandler> proxySupplier) {
        this.proxySupplier = proxySupplier;
    }

    public void setProxySupplier(Supplier<ProxyFrontendHandler> proxySupplier) {
        this.proxySupplier = proxySupplier;
    }

    @Override
    public String getProtocolName() {
        return "dubbo-proxy";
    }

    @Override
    public boolean canSupport(ByteBuf buffer) {
        return DubboDecoder.isDubboProtocol(buffer);
    }

    @Override
    public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
        channel.pipeline().addLast(new DubboDecoder());
        channel.pipeline().addLast(proxySupplier.get());
    }
}