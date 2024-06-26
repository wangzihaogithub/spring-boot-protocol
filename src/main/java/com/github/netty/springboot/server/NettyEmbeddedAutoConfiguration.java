package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.AbortPolicyWithReport;
import com.github.netty.core.util.NettyThreadPoolExecutor;
import com.github.netty.protocol.*;
import com.github.netty.protocol.dubbo.Application;
import com.github.netty.protocol.dubbo.ProxyFrontendHandler;
import com.github.netty.protocol.mqtt.interception.InterceptHandler;
import com.github.netty.protocol.mysql.client.MysqlFrontendBusinessHandler;
import com.github.netty.protocol.mysql.listener.MysqlPacketListener;
import com.github.netty.protocol.mysql.listener.WriterLogFilePacketListener;
import com.github.netty.protocol.mysql.server.MysqlBackendBusinessHandler;
import com.github.netty.protocol.servlet.util.HttpAbortPolicyWithReport;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.SpringUtil;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * The netty container is automatically configured
 *
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
     *
     * @param protocolHandlers protocolHandlers
     * @param serverListeners  serverListeners
     * @param beanFactory      beanFactory
     * @return NettyTcpServerFactory
     */
    @Bean("nettyServerFactory")
    @ConditionalOnMissingBean(NettyTcpServerFactory.class)
    public NettyTcpServerFactory nettyTcpServerFactory(Collection<ProtocolHandler> protocolHandlers,
                                                       Collection<ServerListener> serverListeners,
                                                       BeanFactory beanFactory) {
        Supplier<DynamicProtocolChannelHandler> handlerSupplier = () -> {
            Class<? extends DynamicProtocolChannelHandler> type = nettyProperties.getChannelHandler();
            return type == DynamicProtocolChannelHandler.class ?
                    new DynamicProtocolChannelHandler() : beanFactory.getBean(type);
        };
        NettyTcpServerFactory tcpServerFactory = new NettyTcpServerFactory(nettyProperties, handlerSupplier);
        tcpServerFactory.getProtocolHandlers().addAll(protocolHandlers);
        tcpServerFactory.getServerListeners().addAll(serverListeners);
        return tcpServerFactory;
    }

    /**
     * Add the Dubbo protocol registry
     *
     * @return DubboProtocol
     */
    @Bean("dubboProtocol")
    @ConditionalOnMissingBean(DubboProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.dubbo", name = "enabled", matchIfMissing = false)
    public DubboProtocol dubboProtocol() {
        Supplier<ProxyFrontendHandler> proxySupplier = () -> {
            List<Application> applicationList = convert(nettyProperties.getDubbo().getRoutes());
            return new ProxyFrontendHandler(applicationList);
        };
        return new DubboProtocol(proxySupplier);
    }

    protected List<Application> convert(NettyProperties.Dubbo.ApplicationRoute[] routes) {
        List<Application> applicationList = new ArrayList<>();
        if (routes != null) {
            for (NettyProperties.Dubbo.ApplicationRoute source : routes) {
                String[] address = source.getAddress().split(":", 2);

                Application target = new Application();
                target.setName(source.getApplicationName());
                target.setAddress(new InetSocketAddress(address[0], Integer.parseInt(address[1])));
                target.setAttachmentApplicationName(source.getAttachmentName());
                target.setPathPatterns(source.getPathPatterns());
                target.setDefaultApplication(source.isDefaultApplication());
                applicationList.add(target);
            }
        }
        return applicationList;
    }

    /**
     * Add the RPC protocol registry
     *
     * @param factory factory
     * @return NRpcProtocol
     * @throws ClassNotFoundException ClassNotFoundException
     */
    @Bean("nRpcProtocol")
    @ConditionalOnMissingBean(NRpcProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.nrpc", name = "enabled", matchIfMissing = false)
    public NRpcProtocol nRpcProtocol(ConfigurableBeanFactory factory) throws ClassNotFoundException {
        // Preheat codec
        Class.forName("com.github.netty.protocol.nrpc.codec.DataCodecUtil");

        NRpcProtocolSpringAdapter protocol = new NRpcProtocolSpringAdapter(factory,nettyProperties.getApplication());
        protocol.setMessageMaxLength(nettyProperties.getNrpc().getServerMessageMaxLength());
        protocol.setMethodOverwriteCheck(nettyProperties.getNrpc().isServerMethodOverwriteCheck());
        protocol.setServerDefaultVersion(nettyProperties.getNrpc().getServerDefaultVersion());
        protocol.setExecutorSupplier(newExecutorSupplier(nettyProperties.getNrpc().getThreadPool(), factory));
        return protocol;
    }

    /**
     * Add the HTTP protocol registry
     *
     * @param factory        factory
     * @param resourceLoader resourceLoader
     * @return HttpServletProtocol
     */
    @Bean("httpServletProtocol")
    @ConditionalOnMissingBean(HttpServletProtocol.class)
    public HttpServletProtocol httpServletProtocol(ConfigurableBeanFactory factory, ResourceLoader resourceLoader) {
        NettyProperties.HttpServlet http = nettyProperties.getHttpServlet();
        Supplier<Executor> executorSupplier = newExecutorSupplier(http.getThreadPool(), factory);
        Supplier<Executor> defaultExecutorSupplier = newDefaultExecutorSupplier(http.getThreadPool(), factory);

        HttpServletProtocolSpringAdapter protocol = new HttpServletProtocolSpringAdapter(
                nettyProperties, resourceLoader.getClassLoader(), executorSupplier, defaultExecutorSupplier);
        protocol.setMaxInitialLineLength(http.getRequestMaxHeaderLineSize());
        protocol.setMaxHeaderSize(http.getRequestMaxHeaderSize());
        protocol.setMaxContentLength(http.getRequestMaxContentSize());
        protocol.setMaxBufferBytes(http.getResponseMaxBufferSize());
        protocol.setAutoFlushIdleMs(http.getAutoFlushIdleMs());
        protocol.setMultipartPropertiesSupplier(() -> SpringUtil.getBean(factory, MultipartProperties.class));
        protocol.setServerPropertiesSupplier(() -> SpringUtil.getBean(factory, ServerProperties.class));
        return protocol;
    }

    /**
     * Add the MQTT protocol registry
     *
     * @return MqttProtocol
     */
    @Bean("mqttProtocol")
    @ConditionalOnMissingBean(MqttProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.mqtt", name = "enabled", matchIfMissing = false)
    public MqttProtocol mqttProtocol(ListableBeanFactory beanFactory) {
        NettyProperties.Mqtt mqtt = nettyProperties.getMqtt();
        MqttProtocol protocol = new MqttProtocol(mqtt.getMessageMaxLength(), mqtt.getNettyReaderIdleTimeSeconds(), mqtt.getAutoFlushIdleMs());
        beanFactory.getBeansOfType(InterceptHandler.class).values().forEach(protocol::addInterceptHandler);
        return protocol;
    }

    /**
     * Add the MYSQL protocol registry
     *
     * @param beanFactory          ListableBeanFactory
     * @param mysqlPacketListeners MysqlPacketListener
     * @return MysqlProtocol
     */
    @Bean("mysqlProtocol")
    @ConditionalOnMissingBean(MysqlProtocol.class)
    @ConditionalOnProperty(prefix = "server.netty.mysql", name = "enabled", matchIfMissing = false)
    public MysqlProtocol mysqlServerProtocol(ListableBeanFactory beanFactory,
                                             @Autowired(required = false) Collection<MysqlPacketListener> mysqlPacketListeners) {
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        MysqlProtocol protocol = new MysqlProtocol(new InetSocketAddress(mysql.getMysqlHost(), mysql.getMysqlPort()));
        protocol.setMaxPacketSize(mysql.getPacketMaxLength());
        if (mysqlPacketListeners != null) {
            protocol.getMysqlPacketListeners().addAll(mysqlPacketListeners);
        }
        protocol.getMysqlPacketListeners().sort(AnnotationAwareOrderComparator.INSTANCE);

        if (mysql.getFrontendBusinessHandler() != MysqlFrontendBusinessHandler.class) {
            String[] names = beanFactory.getBeanNamesForType(mysql.getFrontendBusinessHandler());
            for (String name : names) {
                if (beanFactory.isSingleton(name)) {
                    throw new AssertionError("\nNettyProperties AssertionError(!isSingleton('" + name + "')) -> \n" +
                            "Need is the prototype. please add  -> @org.springframework.context.annotation.Scope(\"prototype\").\n" +
                            "server:\n" +
                            "\tnetty:\n" +
                            "\t\tmysql:\n" +
                            "\t\t\tfrontendBusinessHandler: " + mysql.getFrontendBusinessHandler().getName() + "\n");
                }
            }
            protocol.setFrontendBusinessHandler(() -> beanFactory.getBean(mysql.getFrontendBusinessHandler()));
        }

        if (mysql.getBackendBusinessHandler() != MysqlBackendBusinessHandler.class) {
            String[] names = beanFactory.getBeanNamesForType(mysql.getBackendBusinessHandler());
            for (String name : names) {
                if (beanFactory.isSingleton(name)) {
                    throw new AssertionError("\nNettyProperties AssertionError(!isSingleton('" + name + "')) -> \n" +
                            "Need is the prototype. please add  -> @org.springframework.context.annotation.Scope(\"prototype\").\n" +
                            "server:\n" +
                            "\tnetty:\n" +
                            "\t\tmysql:\n" +
                            "\t\t\tbackendBusinessHandler: " + mysql.getBackendBusinessHandler().getName() + "\n");
                }
            }
            protocol.setBackendBusinessHandler(() -> beanFactory.getBean(mysql.getBackendBusinessHandler()));
        }
        return protocol;
    }

    /**
     * mysql proxy WriterLogFilePacketListener
     *
     * @param environment Environment
     * @return WriterLogFilePacketListener
     */
    @Bean("mysqlWriterLogFilePacketListener")
    @ConditionalOnMissingBean(WriterLogFilePacketListener.class)
    @ConditionalOnProperty(prefix = "server.netty.mysql", name = {"enabled"}, matchIfMissing = false)
    public WriterLogFilePacketListener mysqlWriterLogFilePacketListener(Environment environment) {
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        WriterLogFilePacketListener listener = new WriterLogFilePacketListener();
        listener.setEnable(mysql.getProxyLog().isEnable());
        listener.setLogFileName(environment.resolvePlaceholders(mysql.getProxyLog().getLogFileName()));
        listener.setLogPath(environment.resolvePlaceholders(mysql.getProxyLog().getLogPath()));
        listener.setLogWriteInterval(mysql.getProxyLog().getLogFlushInterval());
        return listener;
    }

    protected Supplier<Executor> newExecutorSupplier(NettyProperties.HttpServlet.ServerThreadPool pool, ConfigurableBeanFactory factory) {
        Supplier<Executor> executorSupplier;
        if (pool.isEnable()) {
            if (pool.getExecutor() == NettyThreadPoolExecutor.class) {
                RejectedExecutionHandler rejectedHandler;
                if (pool.getRejected() == HttpAbortPolicyWithReport.class) {
                    rejectedHandler = new HttpAbortPolicyWithReport(pool.getPoolName(), pool.getDumpPath(), "HttpServlet");
                } else {
                    rejectedHandler = factory.getBean(pool.getRejected());
                }

                String poolName = pool.getPoolName();
                int coreThreads = pool.getCoreThreads();
                int maxThreads = pool.getMaxThreads();
                int queues = pool.getQueues();
                int keepAliveSeconds = pool.getKeepAliveSeconds();
                boolean allowCoreThreadTimeOut = pool.isAllowCoreThreadTimeOut();
                NettyThreadPoolExecutor executor = newNettyThreadPoolExecutor(poolName, coreThreads, maxThreads, queues, keepAliveSeconds, allowCoreThreadTimeOut, rejectedHandler);
                executorSupplier = () -> executor;
            } else {
                Executor executor = factory.getBean(pool.getExecutor());
                executorSupplier = () -> executor;
            }
        } else {
            executorSupplier = () -> null;
        }
        return executorSupplier;
    }

    protected Supplier<Executor> newDefaultExecutorSupplier(NettyProperties.HttpServlet.ServerThreadPool pool, ConfigurableBeanFactory factory) {
        RejectedExecutionHandler rejectedHandler;
        if (pool.getRejected() == HttpAbortPolicyWithReport.class) {
            rejectedHandler = new HttpAbortPolicyWithReport(pool.getPoolName(), pool.getDumpPath(), "Default Pool HttpServlet");
        } else {
            rejectedHandler = factory.getBean(pool.getRejected());
        }
        return new LazyPool(this, pool, rejectedHandler);
    }

    protected Supplier<Executor> newExecutorSupplier(NettyProperties.Nrpc.ServerThreadPool pool, ConfigurableBeanFactory factory) {
        Supplier<Executor> executorSupplier;
        if (pool.isEnable()) {
            if (pool.getExecutor() == NettyThreadPoolExecutor.class) {
                RejectedExecutionHandler rejectedHandler;
                if (pool.getRejected() == AbortPolicyWithReport.class) {
                    rejectedHandler = new AbortPolicyWithReport(pool.getPoolName(), pool.getDumpPath(), "Nrpc");
                } else {
                    rejectedHandler = factory.getBean(pool.getRejected());
                }

                String poolName = pool.getPoolName();
                int coreThreads = pool.getCoreThreads();
                int maxThreads = pool.getMaxThreads();
                int queues = pool.getQueues();
                int keepAliveSeconds = pool.getKeepAliveSeconds();
                boolean allowCoreThreadTimeOut = pool.isAllowCoreThreadTimeOut();
                NettyThreadPoolExecutor executor = newNettyThreadPoolExecutor(poolName, coreThreads, maxThreads, queues, keepAliveSeconds, allowCoreThreadTimeOut, rejectedHandler);
                executorSupplier = () -> executor;
            } else {
                executorSupplier = () -> factory.getBean(pool.getExecutor());
            }
        } else {
            executorSupplier = () -> null;
        }
        return executorSupplier;
    }

    protected NettyThreadPoolExecutor newNettyThreadPoolExecutor(
            String poolName,
            int coreThreads,
            int maxThreads,
            int queues,
            int keepAliveSeconds,
            boolean allowCoreThreadTimeOut,
            RejectedExecutionHandler handler) {
        BlockingQueue<Runnable> workQueue = queues == 0 ?
                new SynchronousQueue<>() :
                (queues < 0 ? new LinkedBlockingQueue<>(Integer.MAX_VALUE)
                        : new LinkedBlockingQueue<>(queues));
        boolean daemon = true;
        NettyThreadPoolExecutor executor = new NettyThreadPoolExecutor(
                coreThreads, maxThreads, keepAliveSeconds, TimeUnit.SECONDS,
                workQueue, poolName, Thread.NORM_PRIORITY, daemon, handler);
        executor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return executor;
    }

    public static class LazyPool implements Supplier<Executor> {
        protected final NettyProperties.HttpServlet.ServerThreadPool pool;
        protected final RejectedExecutionHandler rejectedHandler;
        protected final NettyEmbeddedAutoConfiguration autoConfiguration;
        protected volatile Executor executor;

        public LazyPool(NettyEmbeddedAutoConfiguration autoConfiguration, NettyProperties.HttpServlet.ServerThreadPool pool, RejectedExecutionHandler rejectedHandler) {
            this.autoConfiguration = autoConfiguration;
            this.pool = pool;
            this.rejectedHandler = rejectedHandler;
        }

        @Override
        public Executor get() {
            if (executor == null) {
                synchronized (this) {
                    if (executor == null) {
                        String poolName = pool.getPoolName();
                        int coreThreads = pool.getCoreThreads();
                        int maxThreads = pool.getMaxThreads();
                        int queues = pool.getQueues();
                        int keepAliveSeconds = pool.getKeepAliveSeconds();
                        boolean allowCoreThreadTimeOut = pool.isAllowCoreThreadTimeOut();
                        executor = autoConfiguration.newNettyThreadPoolExecutor(poolName, coreThreads, maxThreads, queues, keepAliveSeconds, allowCoreThreadTimeOut, rejectedHandler);
                    }
                }
            }
            return executor;
        }
    }

}
