package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.protocol.*;
import com.github.netty.protocol.mysql.client.MysqlClientBusinessHandler;
import com.github.netty.protocol.mysql.server.MysqlServerBusinessHandler;
import com.github.netty.springboot.NettyProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.net.InetSocketAddress;
import java.util.Collection;
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
     * @param beanFactory beanFactory
     * @return NettyTcpServerFactory
     */
    @Bean("nettyServerFactory")
    @ConditionalOnMissingBean(NettyTcpServerFactory.class)
    public NettyTcpServerFactory nettyTcpServerFactory(Collection<ProtocolHandler> protocolHandlers,
                                                       Collection<ServerListener> serverListeners,
                                                       BeanFactory beanFactory){
        Supplier<DynamicProtocolChannelHandler> handlerSupplier = ()->{
            Class<?extends DynamicProtocolChannelHandler> type = nettyProperties.getChannelHandler();
            return type == DynamicProtocolChannelHandler.class?
                    new DynamicProtocolChannelHandler() : beanFactory.getBean(type);
        };
        NettyTcpServerFactory tcpServerFactory = new NettyTcpServerFactory(nettyProperties,handlerSupplier);
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

    /**
     * Add the MYSQL protocol registry
     * @param factory BeanFactory
     * @return MysqlServerProtocol
     */
    @Bean("mysqlServerProtocol")
    @ConditionalOnMissingBean(MysqlProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.mysql", name = "enabled", matchIfMissing = false)
    public MysqlProtocol mysqlServerProtocol(BeanFactory factory){
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        Class<? extends MysqlClientBusinessHandler> clientBusinessHandler = mysql.getClientBusinessHandler();
        Class<? extends MysqlServerBusinessHandler> serverBusinessHandler = mysql.getServerBusinessHandler();

        Supplier<MysqlClientBusinessHandler> clientSupplier = clientBusinessHandler == MysqlClientBusinessHandler.class?
                MysqlClientBusinessHandler::new : ()-> factory.getBean(clientBusinessHandler);
        Supplier<MysqlServerBusinessHandler> serverSupplier = serverBusinessHandler == MysqlServerBusinessHandler.class?
                MysqlServerBusinessHandler::new : ()-> factory.getBean(serverBusinessHandler);

        MysqlProtocol protocol = new MysqlProtocol(serverSupplier,clientSupplier);
        protocol.setMaxPacketSize(mysql.getPacketMaxLength());
        protocol.setMysqlAddress(new InetSocketAddress(mysql.getMysqlHost(), mysql.getMysqlPort()));
        return protocol;
    }

}
