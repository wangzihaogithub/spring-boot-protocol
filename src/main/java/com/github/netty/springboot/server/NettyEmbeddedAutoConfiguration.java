package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.AbortPolicyWithReport;
import com.github.netty.core.util.NettyThreadPoolExecutor;
import com.github.netty.protocol.*;
import com.github.netty.protocol.mysql.client.MysqlFrontendBusinessHandler;
import com.github.netty.protocol.mysql.listener.MysqlPacketListener;
import com.github.netty.protocol.mysql.listener.WriterLogFilePacketListener;
import com.github.netty.protocol.mysql.server.MysqlBackendBusinessHandler;
import com.github.netty.springboot.NettyProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.*;
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
        protocol.setMethodOverwriteCheck(nettyProperties.getNrpc().isServerMethodOverwriteCheck());
        protocol.setServerDefaultVersion(nettyProperties.getNrpc().getServerDefaultVersion());
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
        NettyProperties.HttpServlet http = nettyProperties.getHttpServlet();
        NettyProperties.HttpServlet.ServerThreadPool pool = nettyProperties.getHttpServlet().getServerThreadPool();

        Supplier<Executor> executorSupplier;
        if(pool.getExecutor() == NettyThreadPoolExecutor.class){
            RejectedExecutionHandler rejectedHandler;
            if(pool.getRejected() == AbortPolicyWithReport.class){
                rejectedHandler = new AbortPolicyWithReport(pool.getPoolName(),pool.getDumpPath(),"HttpServlet");
            }else {
                rejectedHandler = factory.getBean(pool.getRejected());
            }
            NettyThreadPoolExecutor executor = newNettyThreadPoolExecutor(
                    pool.getPoolName(), pool.getCoreThreads(), pool.getMaxThreads(), pool.getQueues(),
                    pool.getKeepAliveSeconds(), pool.isFixed(), rejectedHandler);
            executorSupplier = ()-> executor;
        }else {
            executorSupplier = () -> factory.getBean(pool.getExecutor());
        }

        HttpServletProtocolSpringAdapter protocol = new HttpServletProtocolSpringAdapter(nettyProperties,executorSupplier,resourceLoader.getClassLoader());
        protocol.setMaxInitialLineLength(http.getRequestMaxHeaderLineSize());
        protocol.setMaxHeaderSize(http.getRequestMaxHeaderSize());
        protocol.setMaxContentLength(http.getRequestMaxContentSize());
        protocol.setMaxChunkSize(http.getRequestMaxChunkSize());
        protocol.setMaxBufferBytes(http.getResponseMaxBufferSize());

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
     * @param beanFactory ListableBeanFactory
     * @param mysqlPacketListeners MysqlPacketListener
     * @return MysqlProtocol
     */
    @Bean("mysqlProtocol")
    @ConditionalOnMissingBean(MysqlProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.mysql", name = "enabled", matchIfMissing = false)
    public MysqlProtocol mysqlServerProtocol(ListableBeanFactory beanFactory,
                                             @Autowired(required = false) Collection<MysqlPacketListener> mysqlPacketListeners){
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        MysqlProtocol protocol = new MysqlProtocol(new InetSocketAddress(mysql.getMysqlHost(), mysql.getMysqlPort()));
        protocol.setMaxPacketSize(mysql.getPacketMaxLength());
        if(mysqlPacketListeners != null) {
            protocol.getMysqlPacketListeners().addAll(mysqlPacketListeners);
        }
        protocol.getMysqlPacketListeners().sort(AnnotationAwareOrderComparator.INSTANCE);

        if(mysql.getFrontendBusinessHandler() != MysqlFrontendBusinessHandler.class){
            String[] names = beanFactory.getBeanNamesForType(mysql.getFrontendBusinessHandler());
            for (String name : names) {
                if(beanFactory.isSingleton(name)) {
                    throw new AssertionError("\nNettyProperties AssertionError(!isSingleton('"+name+"')) -> \n" +
                            "Need is the prototype. please add  -> @org.springframework.context.annotation.Scope(\"prototype\").\n" +
                            "server:\n" +
                            "\tnetty:\n" +
                            "\t\tmysql:\n" +
                            "\t\t\tfrontendBusinessHandler: "+ mysql.getFrontendBusinessHandler().getName()+"\n");
                }
            }
            protocol.setFrontendBusinessHandler(()-> beanFactory.getBean(mysql.getFrontendBusinessHandler()));
        }

        if(mysql.getBackendBusinessHandler() != MysqlBackendBusinessHandler.class){
            String[] names = beanFactory.getBeanNamesForType(mysql.getBackendBusinessHandler());
            for (String name : names) {
                if(beanFactory.isSingleton(name)) {
                    throw new AssertionError("\nNettyProperties AssertionError(!isSingleton('"+name+"')) -> \n" +
                            "Need is the prototype. please add  -> @org.springframework.context.annotation.Scope(\"prototype\").\n" +
                            "server:\n" +
                            "\tnetty:\n" +
                            "\t\tmysql:\n" +
                            "\t\t\tbackendBusinessHandler: "+ mysql.getBackendBusinessHandler().getName()+"\n");
                }
            }
            protocol.setBackendBusinessHandler(()-> beanFactory.getBean(mysql.getBackendBusinessHandler()));
        }
        return protocol;
    }

    /**
     * mysql proxy WriterLogFilePacketListener
     * @param environment Environment
     * @return WriterLogFilePacketListener
     */
    @Bean("mysqlWriterLogFilePacketListener")
    @ConditionalOnMissingBean(WriterLogFilePacketListener.class)
    @ConditionalOnProperty(prefix = "server.netty.mysql", name = {"enabled"}, matchIfMissing = false)
    public WriterLogFilePacketListener mysqlWriterLogFilePacketListener(Environment environment){
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        WriterLogFilePacketListener listener = new WriterLogFilePacketListener();
        listener.setEnable(mysql.getProxyLog().isEnable());
        listener.setLogFileName(environment.resolvePlaceholders(mysql.getProxyLog().getLogFileName()));
        listener.setLogPath(environment.resolvePlaceholders(mysql.getProxyLog().getLogPath()));
        listener.setLogWriteInterval(mysql.getProxyLog().getLogFlushInterval());
        return listener;
    }

    private NettyThreadPoolExecutor newNettyThreadPoolExecutor(String poolName,int coreThreads,int maxThreads,int queues,
                                                               int keepAliveSeconds,boolean fixed,RejectedExecutionHandler handler){
        BlockingQueue<Runnable> workQueue = queues == 0 ?
                new SynchronousQueue<>() :
                (queues < 0 ? new LinkedBlockingQueue<>(Integer.MAX_VALUE)
                        : new LinkedBlockingQueue<>(queues));
        if(fixed){
            int max = Math.max(coreThreads,maxThreads);
            coreThreads = max;
            maxThreads = max;
        }
        int priority = Thread.NORM_PRIORITY;
        boolean daemon = false;
        return new NettyThreadPoolExecutor(
                coreThreads,maxThreads,keepAliveSeconds, TimeUnit.SECONDS,
                workQueue,poolName,priority,daemon,handler);
    }

}
