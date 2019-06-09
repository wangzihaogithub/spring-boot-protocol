package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.NRpcProtocol;
import com.github.netty.springboot.NettyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * The netty container is automatically configured
 * @author wangzihao
 */
@Configuration
@AutoConfigureAfter(NettyProperties.class)
@EnableConfigurationProperties(NettyProperties.class)
public class NettyEmbeddedAutoConfiguration {
    @Autowired
    private NettyProperties nettyProperties;

    /**
     * Add a TCP service factory
     * @param protocolHandlers protocolHandlers
     * @param serverListeners serverListeners
     * @return NettyTcpServerFactory
     */
    @Bean("nettyServerFactory")
    @ConditionalOnMissingBean(NettyTcpServerFactory.class)
    public NettyTcpServerFactory nettyTcpServerFactory(Collection<ProtocolHandler> protocolHandlers,
                                                       Collection<ServerListener> serverListeners){
        NettyTcpServerFactory tcpServerFactory = new NettyTcpServerFactory(
                nettyProperties,
                new TreeSet<>(Comparator.comparingInt(ProtocolHandler::order)),
                new TreeSet<>(Comparator.comparingInt(ServerListener::order))
                );
        tcpServerFactory.getProtocolHandlers().addAll(protocolHandlers);
        tcpServerFactory.getServerListeners().addAll(serverListeners);
        return tcpServerFactory;
    }

    /**
     * Add the RPC protocol registry
     * @return NRpcProtocol
     */
    @Bean("hRpcProtocolsRegister")
    @ConditionalOnMissingBean(NRpcProtocol.class)
    public NRpcProtocol hRpcProtocolsRegister(){
        return new HRpcProtocolSpringAdapter(nettyProperties.getRpcServerMessageMaxLength(),nettyProperties.getApplication());
    }

    /**
     * Add the HTTP protocol registry
     * @param factory factory
     * @param resourceLoader resourceLoader
     * @return HttpServletProtocol
     */
    @Bean("httpServletProtocolsRegister")
    @ConditionalOnMissingBean(HttpServletProtocol.class)
    public HttpServletProtocol httpServletProtocolsRegister(ConfigurableBeanFactory factory, ResourceLoader resourceLoader) {
        HttpServletProtocolSpringAdapter httpServletProtocolsRegister = new HttpServletProtocolSpringAdapter(nettyProperties,resourceLoader.getClassLoader());
        httpServletProtocolsRegister.setMaxInitialLineLength(4096);
        httpServletProtocolsRegister.setMaxHeaderSize(8192);
        httpServletProtocolsRegister.setMaxContentLength(5 * 1024 * 1024);
        httpServletProtocolsRegister.setMaxChunkSize(5 * 1024 * 1024);

        factory.addBeanPostProcessor(httpServletProtocolsRegister);
        return httpServletProtocolsRegister;
    }

}
