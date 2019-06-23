package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.MqttProtocol;
import com.github.netty.protocol.NRpcProtocol;
import com.github.netty.springboot.NettyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @Bean("nRpcProtocol")
    @ConditionalOnMissingBean(NRpcProtocol.class)
    public NRpcProtocol nRpcProtocol(){
        HRpcProtocolSpringAdapter adapter = new HRpcProtocolSpringAdapter(nettyProperties.getApplication());
        adapter.setMessageMaxLength(nettyProperties.getNrpc().getServerMessageMaxLength());
        return adapter;
    }

    /**
     * Add the HTTP protocol registry
     * @param factory factory
     * @param resourceLoader resourceLoader
     * @return HttpServletProtocol
     */
    @Bean("httpServletProtocol")
    @ConditionalOnMissingBean(HttpServletProtocol.class)
    public HttpServletProtocol httpServletProtocol(ConfigurableBeanFactory factory, ResourceLoader resourceLoader) {
        HttpServletProtocolSpringAdapter httpServletProtocolsRegister = new HttpServletProtocolSpringAdapter(nettyProperties,resourceLoader.getClassLoader());
        NettyProperties.HttpServlet http = nettyProperties.getHttpServlet();
        httpServletProtocolsRegister.setMaxInitialLineLength(http.getMaxHeaderLineSize());
        httpServletProtocolsRegister.setMaxHeaderSize(http.getMaxHeaderSize());
        httpServletProtocolsRegister.setMaxContentLength(http.getMaxContentSize());
        httpServletProtocolsRegister.setMaxChunkSize(http.getMaxChunkSize());

        factory.addBeanPostProcessor(httpServletProtocolsRegister);
        return httpServletProtocolsRegister;
    }

    /**
     * Add the MQTT protocol registry
     * @return MqttProtocol
     */
    @Bean("mqttProtocol")
    @ConditionalOnMissingBean(MqttProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.mqtt", name = "enabled", matchIfMissing = false)
    public MqttProtocol mqttProtocol(){
        NettyProperties.Mqtt mqtt = nettyProperties.getMqtt();
        return new MqttProtocol(mqtt.getMessageMaxLength(),mqtt.getNettyReaderIdleTimeSeconds(),mqtt.getAutoFlushIdleTime());
    }

}
