package com.github.netty.springboot.server;

import com.github.netty.core.Ordered;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.MqttProtocol;
import com.github.netty.protocol.NRpcProtocol;
import com.github.netty.springboot.NettyProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The netty container is automatically configured
 * @author wangzihao
 */
@Configuration
@AutoConfigureAfter(NettyProperties.class)
@EnableConfigurationProperties(NettyProperties.class)
public class NettyEmbeddedAutoConfiguration {
    private final NettyProperties nettyProperties;
    public NettyEmbeddedAutoConfiguration(NettyProperties nettyProperties) {
        this.nettyProperties = nettyProperties;
    }

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
                new TreeSet<>(Ordered.COMPARATOR),
                new TreeSet<>(Ordered.COMPARATOR)
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
        HRpcProtocolSpringAdapter protocol = new HRpcProtocolSpringAdapter(nettyProperties.getApplication());
        protocol.setMessageMaxLength(nettyProperties.getNrpc().getServerMessageMaxLength());
        return protocol;
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
        Class<? extends Executor> serverHandlerExecutorClass = nettyProperties.getHttpServlet().getServerHandlerExecutor();
        Supplier<Executor> serverHandlerExecutor = null;
        if(serverHandlerExecutorClass != null){
            serverHandlerExecutor = () -> factory.getBean(serverHandlerExecutorClass);
        }

        HttpServletProtocolSpringAdapter protocol = new HttpServletProtocolSpringAdapter(nettyProperties,serverHandlerExecutor,resourceLoader.getClassLoader());
        NettyProperties.HttpServlet http = nettyProperties.getHttpServlet();
        protocol.setMaxInitialLineLength(http.getMaxHeaderLineSize());
        protocol.setMaxHeaderSize(http.getMaxHeaderSize());
        protocol.setMaxContentLength(http.getMaxContentSize());
        protocol.setMaxChunkSize(http.getMaxChunkSize());

        factory.addBeanPostProcessor(protocol);
        return protocol;
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
