package com.github.netty.protocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.TcpChannel;
import com.github.netty.core.util.ChunkedWriteHandler;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.ResourceManager;
import com.github.netty.protocol.servlet.*;
import com.github.netty.protocol.servlet.ssl.SslContextBuilders;
import com.github.netty.protocol.servlet.util.*;
import com.github.netty.protocol.servlet.websocket.WebSocketHandler;
import com.github.netty.protocol.servlet.websocket.WebsocketServletUpgrader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.AsciiString;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * HttpServlet protocol registry
 *
 * @author wangzihao
 * 2018/11/11/011
 */
public class HttpServletProtocol extends AbstractProtocol {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(HttpServletProtocol.class);
    private static final boolean EXIST_JAVAX_WEBSOCKET;
    private static final ByteBuf OUT_OF_MAX_CONNECTION_RESPONSE = Unpooled.copiedBuffer(
            "HTTP/1.1 503\r\n" +
                    "Retry-After: 60\r\n" +
                    "Connection: Close\r\n" +
                    "Content-Length: 0\r\n" +
                    "\r\n", Charset.forName("ISO-8859-1"));

    static {
        boolean existJavaxWebsocket;
        try {
            Class.forName("javax.websocket.Endpoint");
            existJavaxWebsocket = true;
        } catch (Throwable e) {
            existJavaxWebsocket = false;
        }
        EXIST_JAVAX_WEBSOCKET = existJavaxWebsocket;
    }

    private final ServletContext servletContext;
    private SslContextBuilder sslContextBuilder;
    private SslContext sslContext;
    private long maxContentLength = 20 * 1024 * 1024;
    private int maxInitialLineLength = 40960;
    private int maxHeaderSize = 81920;
    private int maxChunkSize = 5 * 1024 * 1024;
    private int http2MaxReservedStreams = 256;
    private boolean enableContentCompression = true;
    private boolean enableH2c = false;
    private boolean enableH2 = true;

    private int contentSizeThreshold = 8102;
    private String[] compressionMimeTypes = {"text/html", "text/xml", "text/plain",
            "text/css", "text/javascript", "application/javascript", "application/json", "application/xml"};
    private boolean onServerStart = false;
    private /*volatile*/ WebsocketServletUpgrader websocketServletUpgrader;

    public HttpServletProtocol(ServletContext servletContext) {
        this(servletContext, null, null);
    }

    public HttpServletProtocol(ServletContext servletContext, Supplier<Executor> executorSupplier, Supplier<Executor> defaultExecutorSupplier) {
        this.servletContext = servletContext;
        if (defaultExecutorSupplier == null) {
            defaultExecutorSupplier = new HttpLazyThreadPool("NettyX-http");
        }
        servletContext.setDefaultExecutorSupplier(defaultExecutorSupplier);
        servletContext.setAsyncExecutorSupplier(executorSupplier);
    }

    public boolean addWebSocketHandler(String pathPattern, WebSocketHandler handler) {
        return getWebsocketServletUpgrader().addHandler(pathPattern, handler);
    }

    public WebsocketServletUpgrader getWebsocketServletUpgrader() {
        if (websocketServletUpgrader == null) {
            synchronized (this) {
                if (websocketServletUpgrader == null) {
                    websocketServletUpgrader = new WebsocketServletUpgrader();
                }
            }
        }
        return websocketServletUpgrader;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        servletContext.setServerAddress(server.getServerAddress());
        configurableServletContext();

        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if (listenerManager.hasServletContextListener()) {
            listenerManager.onServletContextInitialized(new ServletContextEvent(servletContext));
        }

        //Servlet will be initialized automatically before use.
        initFilter(servletContext);
        listenerManager.onServletDefaultInitializer(servletContext.getDefaultServlet(), servletContext);

        listenerManager.onServletContainerInitializerStartup(Collections.emptySet(), servletContext);

        if (sslContextBuilder != null) {
            this.sslContext = SslContextBuilders.newSslContext(sslContextBuilder, enableH2);
        }
        this.onServerStart = true;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStop(T server) {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if (listenerManager.hasServletContextListener()) {
            listenerManager.onServletContextDestroyed(new ServletContextEvent(servletContext));
        }

        destroyFilter();
        destroyServlet();
    }

    @Override
    public boolean onOutOfMaxConnection(ByteBuf clientFirstMsg, TcpChannel tcpChannel,
                                        int currentConnections,
                                        int maxConnections) {
        OUT_OF_MAX_CONNECTION_RESPONSE.retain();
        tcpChannel.writeAndFlush(OUT_OF_MAX_CONNECTION_RESPONSE);
        return false;
    }

    protected void configurableServletContext() throws Exception {
        if (servletContext.getResourceManager() == null) {
            servletContext.setDocBase(ResourceManager.createTempDir("netty-docbase").getAbsolutePath());
        }
    }

    /**
     * Initialization filter
     *
     * @param servletContext servletContext
     */
    protected void initFilter(ServletContext servletContext) throws ServletException {
        Map<String, ServletFilterRegistration> servletFilterRegistrationMap = servletContext.getFilterRegistrations();
        for (ServletFilterRegistration registration : servletFilterRegistrationMap.values()) {
            if (registration.isInitFilterCas(false, true)) {
                registration.getFilter().init(registration.getFilterConfig());
            }
        }
    }

    /**
     * Destruction filter
     */
    protected void destroyFilter() {
        Map<String, ServletFilterRegistration> servletRegistrationMap = servletContext.getFilterRegistrations();
        for (ServletFilterRegistration registration : servletRegistrationMap.values()) {
            Filter filter = registration.getFilter();
            if (filter == null) {
                continue;
            }
            if (registration.isInitFilter()) {
                try {
                    filter.destroy();
                } catch (Exception e) {
                    LOGGER.error("destroyFilter error={},filter={}", e.toString(), filter, e);
                }
            }
        }
    }

    /**
     * Destruction servlet
     */
    protected void destroyServlet() {
        Map<String, ServletRegistration> servletRegistrationMap = servletContext.getServletRegistrations();
        for (ServletRegistration registration : servletRegistrationMap.values()) {
            Servlet servlet = registration.getServlet();
            if (servlet == null) {
                continue;
            }
            if (registration.isInitServlet()) {
                try {
                    servlet.destroy();
                } catch (Exception e) {
                    LOGGER.error("destroyServlet error={},servlet={}", e.toString(), servlet, e);
                }
            }
        }
        Servlet defaultServlet = this.servletContext.getDefaultServlet();
        if (onServerStart && defaultServlet != null) {
            try {
                defaultServlet.destroy();
            } catch (Exception e) {
                LOGGER.error("destroyServlet error={},servlet={}", e.toString(), defaultServlet, e);
            }
        }
    }

    public boolean isEnableSsl() {
        return sslContext != null;
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        if (isEnableSsl()) {
            return true;
        }
        return Protocol.isHttpPacket(msg);
    }

    @Override
    public void addPipeline(Channel ch, ByteBuf clientFirstMsg) throws Exception {
        super.addPipeline(ch, clientFirstMsg);
        ChannelPipeline pipeline = ch.pipeline();
        if (isEnableSsl()) {
            pipeline.addLast(sslContext.newHandler(ch.alloc()));
            pipeline.addLast(new SslUpgradeHandler());
        } else if (Protocol.isPriHttp2(clientFirstMsg)) {
            pipeline.addLast(newHttp2Handler(getH2LogLevel(pipeline)));
            addServletPipeline(pipeline, Protocol.h2c_prior_knowledge);
            LOGGER.debug("upgradeToProtocol = h2c_prior_knowledge");
        } else {
            pipeline.addLast(new HttpUpgradeHandler());
        }
    }

    public void addServletPipeline(ChannelPipeline pipeline, Protocol protocol) {
        if (!protocol.isHttp2()) {
            pipeline.addLast(new HttpContentDecompressor(false));
            //The content of compression
            if (enableContentCompression) {
                pipeline.addLast(new HttpContentCompressor(contentSizeThreshold));
            }
        }

        // ByteBuf to HttpContent
        pipeline.addLast(ByteBufToHttpContentChannelHandler.INSTANCE);

        //Chunked transfer
        pipeline.addLast(new ChunkedWriteHandler(this::getMaxBufferBytes));

        //A business scheduler that lets the corresponding Servlet handle the request
        pipeline.addLast(new DispatcherChannelHandler(servletContext, maxContentLength, protocol, isEnableSsl()));
    }

    public boolean isEnableH2c() {
        return enableH2c;
    }

    public void setEnableH2c(boolean enableH2c) {
        this.enableH2c = enableH2c;
    }

    public boolean isEnableH2() {
        return enableH2;
    }

    public void setEnableH2(boolean enableH2) {
        this.enableH2 = enableH2;
    }

    public int getHttp2MaxReservedStreams() {
        return http2MaxReservedStreams;
    }

    public void setHttp2MaxReservedStreams(int http2MaxReservedStreams) {
        this.http2MaxReservedStreams = http2MaxReservedStreams;
    }

    private Http2ConnectionHandler newHttp2Handler(LogLevel logLevel) {
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true, http2MaxReservedStreams);
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
                .propagateSettings(false)
                .validateHttpHeaders(true)
                .maxContentLength((int) maxContentLength)
                .build();

        HttpToHttp2FrameCodecConnectionHandlerBuilder build = new HttpToHttp2FrameCodecConnectionHandlerBuilder()
                .frameListener(listener)
                .connection(connection)
                .compressor(enableContentCompression);
        if (logLevel != null) {
            build.frameLogger(new Http2FrameLogger(logLevel));
        }
        return build.build();
    }

    private HttpServerUpgradeHandler.UpgradeCodecFactory newUpgradeCodecFactory(LogLevel logLevel) {
        return new HttpServerUpgradeHandler.UpgradeCodecFactory() {
            @Override
            public HttpServerUpgradeHandler.UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                    return new Http2ServerUpgradeCodec(newHttp2Handler(logLevel));
                } else {
                    return null;
                }
            }
        };
    }

    protected HttpServerCodec newHttpServerCodec() {
        return new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize, false);
    }

    public long getMaxBufferBytes() {
        return servletContext.getMaxBufferBytes();
    }

    public void setMaxBufferBytes(int maxBufferBytes) {
        servletContext.setMaxBufferBytes(maxBufferBytes);
    }

    public void setExecutor(Supplier<Executor> dispatcherExecutor) {
        this.servletContext.setAsyncExecutorSupplier(dispatcherExecutor);
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String getProtocolName() {
        String name = "http";
        if (enableH2c) {
            name = name.concat("/h2c");
        }
        boolean ssl = isEnableSsl();
        if (ssl) {
            name = name.concat("/https");
            if (enableH2) {
                name = name.concat("/h2");
            }
        }
        if (EXIST_JAVAX_WEBSOCKET) {
            name = name.concat("/ws");
            if (ssl) {
                name = name.concat("/wss");
            }
        }
        return name;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    /**
     * @param jksKeyFile  xxx.jks
     * @param jksPassword jks-password
     * @throws CertificateException
     * @throws IOException
     * @throws UnrecoverableKeyException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws KeyManagementException
     */
    public void setSslFileJks(File jksKeyFile, File jksPassword) throws CertificateException, IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        this.sslContextBuilder = SslContextBuilders.newSslContextBuilderJks(jksKeyFile, jksPassword);
    }

    public void setSslFileJks(File jksKeyFile, String jksPassword) throws CertificateException, IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        this.sslContextBuilder = SslContextBuilders.newSslContextBuilderJks(jksKeyFile, jksPassword);
    }

    public void setSslFileCrtPem(File crtFile, File pemFile) {
        this.sslContextBuilder = SslContextBuilders.newSslContextBuilderPem(crtFile, pemFile);
    }

    public SslContextBuilder getSslContextBuilder() {
        return sslContextBuilder;
    }

    public void setSslContextBuilder(SslContextBuilder sslContextBuilder) {
        this.sslContextBuilder = sslContextBuilder;
    }

    public void setMaxContentLength(long maxContentLength) {
        if ((int) maxContentLength != maxContentLength) {
            maxContentLength = Integer.MAX_VALUE;
        }
        this.maxContentLength = maxContentLength;
    }

    public void setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public void setMaxChunkSize(long maxChunkSize) {
        if (maxChunkSize != (int) maxChunkSize) {
            this.maxChunkSize = Integer.MAX_VALUE;
        } else {
            this.maxChunkSize = (int) maxChunkSize;
        }
    }

    public LogLevel getH2LogLevel(ChannelPipeline pipeline) {
        LoggingHandler loggingHandler = pipeline.get(LoggingHandler.class);
        return loggingHandler == null ? null : loggingHandler.level();
    }

    public void setCompressionMimeTypes(String[] compressionMimeTypes) {
        if (compressionMimeTypes == null) {
            this.compressionMimeTypes = new String[0];
        } else {
            this.compressionMimeTypes = compressionMimeTypes;
        }
    }

    public void setEnableContentCompression(boolean enableContentCompression) {
        this.enableContentCompression = enableContentCompression;
    }

    public void setContentSizeThreshold(int contentSizeThreshold) {
        this.contentSizeThreshold = contentSizeThreshold;
    }

    public void upgradeWebsocket(ChannelHandlerContext ctx, HttpRequest request) {
        getWebsocketServletUpgrader().upgradeWebsocket(servletContext, ctx, request, false, 65536);
    }

    class HttpContentCompressor extends io.netty.handler.codec.http.HttpContentCompressor {
        public HttpContentCompressor(int contentSizeThreshold) {
//            super(contentSizeThreshold, new CompressionOptions[0]);
            super(6, 15, 8, contentSizeThreshold);
        }

        @Override
        protected Result beginEncode(HttpResponse response, String acceptEncoding) throws Exception {
            // sendfile not support compression
            if (response instanceof NettyHttpResponse && ((NettyHttpResponse) response).isWriteSendFile()) {
                return null;
            }
            if (compressionMimeTypes.length > 0) {
                List<String> values = response.headers().getAll(HttpHeaderConstants.CONTENT_TYPE);
                for (String mimeType : compressionMimeTypes) {
                    for (String value : values) {
                        if (value.contains(mimeType)) {
                            return super.beginEncode(response, acceptEncoding);
                        }
                    }
                }
            }
            return null;
        }
    }

    class SslUpgradeHandler extends ApplicationProtocolNegotiationHandler {

        protected SslUpgradeHandler() {
            super("upgrade");
        }

        @Override
        protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }

        @Override
        public void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
            ChannelPipeline pipeline = ctx.pipeline();
            switch (protocol) {
                case "upgrade": {
                    pipeline.addLast(new HttpUpgradeHandler());
                    break;
                }
                case ApplicationProtocolNames.HTTP_1_1: {
                    pipeline.addLast(newHttpServerCodec());
                    addServletPipeline(pipeline, Protocol.https1_1);
                    pipeline.fireChannelRegistered();
                    pipeline.fireChannelActive();
                    break;
                }
                case ApplicationProtocolNames.HTTP_2: {
                    pipeline.addLast(newHttp2Handler(getH2LogLevel(pipeline)));
                    addServletPipeline(pipeline, Protocol.h2);
                    pipeline.fireChannelRegistered();
                    pipeline.fireChannelActive();
                    break;
                }
                default: {
                    throw new IllegalStateException("Unknown protocol: " + protocol);
                }
            }
        }
    }

    class HttpUpgradeHandler extends AbstractChannelHandler<HttpRequest, HttpRequest> {
        public HttpUpgradeHandler() {
            super(false);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctx.pipeline().addBefore(ctx.name(), null, newHttpServerCodec());
        }

        @Override
        protected void onMessageReceived(ChannelHandlerContext ctx, HttpRequest request) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.remove(this);
            String upgradeToProtocol = upgrade(ctx, request);
            if (upgradeToProtocol != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("upgradeToProtocol = {}", upgradeToProtocol);
                }
            } else {
                addServletPipeline(pipeline, Protocol.http1_1);
                pipeline.fireChannelRegistered();
                pipeline.fireChannelActive();
                pipeline.fireChannelRead(request);
            }
        }

        public String upgrade(ChannelHandlerContext ctx, HttpRequest request) {
            ChannelPipeline pipeline = ctx.pipeline();
            String upgrade = request.headers().get(HttpHeaderNames.UPGRADE);
            if (upgrade == null) {
                return null;
            }
            List<String> requestedProtocols = HttpHeaderUtil.splitProtocolsHeader(upgrade);
            for (String requestedProtocol : requestedProtocols) {
                switch (requestedProtocol) {
                    case "h2c": {
                        if (!enableH2c) {
                            break;
                        }
                        HttpServerCodec serverCodec = pipeline.get(HttpServerCodec.class);
                        pipeline.addLast(new HttpServerUpgradeHandler(serverCodec, newUpgradeCodecFactory(getH2LogLevel(pipeline)), (int) maxContentLength));
                        addServletPipeline(pipeline, Protocol.h2c);
                        pipeline.fireChannelRegistered();
                        pipeline.fireChannelActive();
                        pipeline.fireChannelRead(request);
                        return requestedProtocol;
                    }
                    case "websocket": {
                        upgradeWebsocket(ctx, request);
                        return requestedProtocol;
                    }
                    default: {
                        break;
                    }
                }
            }
            return null;
        }
    }

}
