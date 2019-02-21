package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolsRegister;
import com.github.netty.protocol.HttpServletProtocolsRegister;
import com.github.netty.protocol.NRpcProtocolsRegister;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.NettyPropertiesAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ResourceLoader;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * netty容器自动配置
 * @author 84215
 */
@AutoConfigureAfter(NettyPropertiesAutoConfiguration.class)
@DependsOn("nettyProperties")
@Configuration
public class NettyEmbeddedAutoConfiguration {
    @Qualifier("nettyProperties")
    @Autowired
    private NettyProperties nettyProperties;

    /**
     * 添加tcp服务工厂
     * @return
     */
    @Bean("nettyServerFactory")
    @ConditionalOnMissingBean(NettyTcpServerFactory.class)
    public NettyTcpServerFactory nettyTcpServerFactory(Collection<ProtocolsRegister> protocolsRegisters){
        NettyTcpServerFactory tcpServerFactory = new NettyTcpServerFactory(nettyProperties, new TreeSet<>(Comparator.comparingInt(ProtocolsRegister::order)));
        tcpServerFactory.getProtocolsRegisters().addAll(protocolsRegisters);
        return tcpServerFactory;
    }

    /**
     * 添加rpc协议注册器
     * @return
     */
    @Bean("hRpcProtocolsRegister")
    @ConditionalOnMissingBean(NRpcProtocolsRegister.class)
    public NRpcProtocolsRegister hRpcProtocolsRegister(){
        return new HRpcProtocolsRegisterSpringAdapter(nettyProperties.getRpcServerMessageMaxLength(),nettyProperties.getApplication());
    }

    /**
     * 添加http协议注册器
     * @return
     */
    @Bean("httpServletProtocolsRegister")
    @ConditionalOnMissingBean(HttpServletProtocolsRegister.class)
    public HttpServletProtocolsRegister httpServletProtocolsRegister(ConfigurableBeanFactory factory, ResourceLoader resourceLoader) {
        HttpServletProtocolsRegisterSpringAdapter httpServletProtocolsRegister = new HttpServletProtocolsRegisterSpringAdapter(nettyProperties,resourceLoader.getClassLoader());
        httpServletProtocolsRegister.setMaxInitialLineLength(4096);
        httpServletProtocolsRegister.setMaxHeaderSize(8192);
        httpServletProtocolsRegister.setMaxContentLength(5 * 1024 * 1024);
        httpServletProtocolsRegister.setMaxChunkSize(5 * 1024 * 1024);

        factory.addBeanPostProcessor(httpServletProtocolsRegister);
        return httpServletProtocolsRegister;
    }

}
