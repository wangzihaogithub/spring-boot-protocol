package com.github.netty.springboot.server;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.StringUtil;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.*;
import com.github.netty.protocol.servlet.util.Protocol;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.SpringUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.server.*;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.util.ClassUtils;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * HttpServlet protocol registry (spring adapter)
 *
 * @author wangzihao
 * 2018/11/12/012
 */
public class HttpServletProtocolSpringAdapter extends HttpServletProtocol implements BeanPostProcessor, BeanFactoryAware {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(HttpServletProtocol.class);
    private final NettyProperties properties;
    private final ApplicationX application;
    private BeanFactory beanFactory;
    private AbstractServletWebServerFactory webServerFactory;

    public HttpServletProtocolSpringAdapter(NettyProperties properties, ClassLoader classLoader,
                                            Supplier<Executor> executorSupplier, Supplier<Executor> defaultExecutorSupplier) {
        super(new ServletContext(classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader), executorSupplier, defaultExecutorSupplier);
        this.properties = properties;
        this.application = properties.getApplication();
    }

    /**
     * skip for {@link NettyRequestUpgradeStrategy}
     *
     * @param ctx     netty ctx
     * @param request netty request
     * @see NettyRequestUpgradeStrategy the handler
     */
    @Override
    public void upgradeWebsocket(ChannelHandlerContext ctx, HttpRequest request) {
        // for spring upgradeWebsocket NettyRequestUpgradeStrategy
        ChannelPipeline pipeline = ctx.pipeline();
        addServletPipeline(pipeline, Protocol.http1_1);
        pipeline.fireChannelRegistered();
        pipeline.fireChannelActive();
        pipeline.fireChannelRead(request);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (SpringUtil.isSingletonBean(beanFactory, beanName)) {
            application.addSingletonBean(bean, beanName, false);
        }
        if (bean instanceof AbstractServletWebServerFactory && ((AbstractServletWebServerFactory) bean).getPort() > 0) {
            this.webServerFactory = (AbstractServletWebServerFactory) bean;
        }
        return bean;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        super.onServerStart(server);

        ServletContext servletContext = getServletContext();
        application.addSingletonBean(servletContext);

        LOGGER.info("Netty servlet on port: {}, with context path '{}'",
                servletContext.getServerAddress().getPort(),
                servletContext.getContextPath()
        );
//        application.scanner("com.github.netty").inject();
    }


    @Override
    protected void configurableServletContext() throws Exception {
        if (webServerFactory == null) {
            return;
        }
        ServletContext servletContext = getServletContext();
        ServerProperties serverProperties = application.getBean(ServerProperties.class, null, false);
        MultipartProperties multipartProperties = application.getBean(MultipartProperties.class, null, false);

        InetSocketAddress address = NettyTcpServerFactory.getServerSocketAddress(webServerFactory.getAddress(), webServerFactory.getPort());
        //Server port
        servletContext.setServerAddress(address);
        servletContext.setEnableLookupFlag(properties.getHttpServlet().isEnableNsLookup());
        servletContext.setAutoFlush(properties.getHttpServlet().getAutoFlushIdleMs() > 0);
        servletContext.setUploadFileTimeoutMs(properties.getHttpServlet().getUploadFileTimeoutMs());
        servletContext.setContextPath(webServerFactory.getContextPath());
        servletContext.setServerHeader(webServerFactory.getServerHeader());
        servletContext.setServletContextName(webServerFactory.getDisplayName());
        servletContext.getErrorPageManager().setShowErrorMessage(properties.getHttpServlet().isShowExceptionMessage());
        //Session timeout
        servletContext.setSessionTimeout((int) webServerFactory.getSession().getTimeout().getSeconds());
        servletContext.setSessionService(newSessionService(properties, servletContext));
        for (MimeMappings.Mapping mapping : webServerFactory.getMimeMappings()) {
            servletContext.getMimeMappings().add(mapping.getExtension(), mapping.getMimeType());
        }
        servletContext.getNotExistBodyParameters().addAll(Arrays.asList(properties.getHttpServlet().getNotExistBodyParameter()));

        Compression compression = webServerFactory.getCompression();
        if (compression != null && compression.getEnabled()) {
            super.setEnableContentCompression(compression.getEnabled());
            super.setContentSizeThreshold((SpringUtil.getNumberBytes(compression, "getMinResponseSize")).intValue());
            super.setCompressionMimeTypes(compression.getMimeTypes().clone());
        }
        if (serverProperties != null) {
            super.setMaxHeaderSize((SpringUtil.getNumberBytes(serverProperties, "getMaxHttpHeaderSize")).intValue());
        }
        super.setEnableH2(webServerFactory.getHttp2().isEnabled());
        super.setEnableH2c(properties.getHttpServlet().isEnableH2c());
        String location = null;
        if (multipartProperties != null && multipartProperties.getEnabled()) {
            Number maxRequestSize = SpringUtil.getNumberBytes(multipartProperties, "getMaxRequestSize");
            Number fileSizeThreshold = SpringUtil.getNumberBytes(multipartProperties, "getFileSizeThreshold");

            super.setMaxChunkSize(maxRequestSize.longValue());
            servletContext.setUploadMinSize(fileSizeThreshold.longValue());
            location = multipartProperties.getLocation();
        }

        if (location != null && !location.isEmpty()) {
            servletContext.setDocBase(location, "");
        } else {
            servletContext.setDocBase(webServerFactory.getDocumentRoot().getAbsolutePath());
        }

        //Error page
        for (ErrorPage errorPage : webServerFactory.getErrorPages()) {
            ServletErrorPage servletErrorPage = new ServletErrorPage(errorPage.getStatusCode(), errorPage.getException(), errorPage.getPath());
            servletContext.getErrorPageManager().add(servletErrorPage);
        }

        Ssl ssl = webServerFactory.getSsl();
        if (ssl != null && ssl.isEnabled()) {
            SslStoreProvider sslStoreProvider = webServerFactory.getSslStoreProvider();
            SslContextBuilder sslContextBuilder = SpringUtil.newSslContext(ssl, sslStoreProvider);
            super.setSslContextBuilder(sslContextBuilder);
        }
    }

    /**
     * New session service
     *
     * @param properties     properties
     * @param servletContext servletContext
     * @return SessionService
     */
    protected SessionService newSessionService(NettyProperties properties, ServletContext servletContext) {
        //Composite session (default local storage)
        SessionService sessionService;
        if (StringUtil.isNotEmpty(properties.getHttpServlet().getSessionRemoteServerAddress())) {
            //Enable session remote storage using RPC
            String remoteSessionServerAddress = properties.getHttpServlet().getSessionRemoteServerAddress();
            InetSocketAddress address;
            if (remoteSessionServerAddress.contains(":")) {
                String[] addressArr = remoteSessionServerAddress.split(":");
                address = new InetSocketAddress(addressArr[0], Integer.parseInt(addressArr[1]));
            } else {
                address = new InetSocketAddress(remoteSessionServerAddress, 80);
            }
            SessionCompositeServiceImpl compositeSessionService = new SessionCompositeServiceImpl(servletContext);
            compositeSessionService.enableRemoteRpcSession(address,
                    80,
                    1,
                    properties.getNrpc().isClientEnableHeartLog(),
                    properties.getNrpc().getClientHeartIntervalTimeMs(),
                    properties.getNrpc().getClientReconnectScheduledIntervalMs());
            sessionService = compositeSessionService;
        } else if (properties.getHttpServlet().isEnablesLocalFileSession()) {
            //Enable session file storage
            sessionService = new SessionLocalFileServiceImpl(servletContext.getResourceManager(), servletContext);
        } else {
            sessionService = new SessionLocalMemoryServiceImpl(servletContext);
        }
        return sessionService;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
