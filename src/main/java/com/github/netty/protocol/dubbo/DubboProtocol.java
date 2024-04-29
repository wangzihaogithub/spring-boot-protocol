package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.util.function.Supplier;

public class DubboProtocol extends AbstractProtocol {
    private final Supplier<ProxyFrontendHandler> proxyBackendHandlerFactory;

    public DubboProtocol(Supplier<ProxyFrontendHandler> proxyBackendHandlerFactory) {
        this.proxyBackendHandlerFactory = proxyBackendHandlerFactory;
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
        channel.pipeline().addLast(proxyBackendHandlerFactory.get());
    }
}