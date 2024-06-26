package com.github.netty.springboot.server;

import com.github.netty.core.AbstractNettyServer;
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
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.server.*;
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
public class HttpServletProtocolSpringAdapter extends HttpServletProtocol {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(HttpServletProtocol.class);
    private final NettyProperties properties;
    private Supplier<ServerProperties> serverPropertiesSupplier;
    private Supplier<MultipartProperties> multipartPropertiesSupplier;

    public HttpServletProtocolSpringAdapter(NettyProperties properties, ClassLoader classLoader,
                                            Supplier<Executor> executorSupplier, Supplier<Executor> defaultExecutorSupplier) {
        super(new ServletContext(classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader), executorSupplier, defaultExecutorSupplier);
        this.properties = properties;
    }

    public void setMultipartPropertiesSupplier(Supplier<MultipartProperties> multipartPropertiesSupplier) {
        this.multipartPropertiesSupplier = multipartPropertiesSupplier;
    }

    public void setServerPropertiesSupplier(Supplier<ServerProperties> serverPropertiesSupplier) {
        this.serverPropertiesSupplier = serverPropertiesSupplier;
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
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        initializerStartup();

        ServletContext servletContext = getServletContext();

        LOGGER.info("Netty servlet on port: {}, with context path '{}'",
                servletContext.getServerAddress().getPort(),
                servletContext.getContextPath()
        );
//        application.scanner("com.github.netty").inject();
    }


    public void configurableServletContext(NettyTcpServerFactory webServerFactory) throws Exception {
        ServletContext servletContext = getServletContext();
        ServerProperties serverProperties = serverPropertiesSupplier != null ? serverPropertiesSupplier.get() : null;
        MultipartProperties multipartProperties = multipartPropertiesSupplier != null ? multipartPropertiesSupplier.get() : null;
        NettyProperties.HttpServlet httpServlet = properties.getHttpServlet();

        InetSocketAddress address = NettyTcpServerFactory.getServerSocketAddress(webServerFactory.getAddress(), webServerFactory.getPort());
        //Server port
        servletContext.setServerAddress(address);
        servletContext.setEnableLookupFlag(httpServlet.isEnableNsLookup());
        servletContext.setAutoFlush(httpServlet.getAutoFlushIdleMs() > 0);
        servletContext.setUploadFileTimeoutMs(httpServlet.getUploadFileTimeoutMs());
        servletContext.setContextPath(webServerFactory.getContextPath());
        servletContext.setServerHeader(webServerFactory.getServerHeader());
        servletContext.setServletContextName(webServerFactory.getDisplayName());
        servletContext.getErrorPageManager().setShowErrorMessage(httpServlet.isShowExceptionMessage());
        //Session timeout
        servletContext.setSessionTimeout((int) webServerFactory.getSession().getTimeout().getSeconds());
        servletContext.setSessionService(newSessionService(properties, servletContext));
        for (MimeMappings.Mapping mapping : webServerFactory.getMimeMappings()) {
            servletContext.getMimeMappings().add(mapping.getExtension(), mapping.getMimeType());
        }
        servletContext.getNotExistBodyParameters().addAll(Arrays.asList(httpServlet.getNotExistBodyParameter()));

        Compression compression = webServerFactory.getCompression();
        if (compression != null && compression.getEnabled()) {
            super.setEnableContentCompression(compression.getEnabled());
            super.setContentSizeThreshold((SpringUtil.getNumberBytes(compression, "getMinResponseSize")).intValue());
            super.setCompressionMimeTypes(compression.getMimeTypes().clone());
        }
        if (serverProperties != null) {
            super.setMaxHeaderSize((SpringUtil.getNumberBytes(serverProperties, "getMaxHttpHeaderSize")).intValue());
        }
        Boolean enableH2 = httpServlet.getEnableH2();
        if (enableH2 == null) {
            enableH2 = webServerFactory.getHttp2().isEnabled();
        }
        // https2
        super.setEnableH2(enableH2);
        // http2
        super.setEnableH2c(httpServlet.isEnableH2c());
        // ws
        super.setEnableWebsocket(httpServlet.isEnableWebsocket());
        // https, wss
        Ssl ssl = webServerFactory.getSsl();
        if (ssl != null && ssl.isEnabled()) {
            SslStoreProvider sslStoreProvider = webServerFactory.getSslStoreProvider();
            SslContextBuilder sslContextBuilder = SpringUtil.newSslContext(ssl, sslStoreProvider);
            super.setSslContextBuilder(sslContextBuilder);
        }

        String location = null;
        if (multipartProperties != null && multipartProperties.getEnabled()) {
            Number maxRequestSize = SpringUtil.getNumberBytes(multipartProperties, "getMaxRequestSize");
            Number fileSizeThreshold = SpringUtil.getNumberBytes(multipartProperties, "getFileSizeThreshold");

            super.setMaxChunkSize(maxRequestSize.longValue());
            servletContext.setFileSizeThreshold(fileSizeThreshold.longValue());
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
        NettyProperties.HttpServlet httpServlet = properties.getHttpServlet();
        if (StringUtil.isNotEmpty(httpServlet.getSessionRemoteServerAddress())) {
            //Enable session remote storage using RPC
            String remoteSessionServerAddress = httpServlet.getSessionRemoteServerAddress();
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
        } else if (httpServlet.isEnablesLocalFileSession()) {
            //Enable session file storage
            sessionService = new SessionLocalFileServiceImpl(servletContext.getResourceManager(), servletContext);
        } else {
            sessionService = new SessionLocalMemoryServiceImpl(servletContext);
        }
        return sessionService;
    }

}
