package com.github.netty.protocol;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.DispatcherChannelHandler;
import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.*;
import com.github.netty.protocol.servlet.util.ByteBufToHttpContentChannelHandler;
import com.github.netty.protocol.servlet.util.HttpAbortPolicyWithReport;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.*;
import io.netty.util.AsciiString;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;

/**
 * HttpServlet protocol registry
 *
 * @author wangzihao
 * 2018/11/11/011
 */
public class HttpServletProtocol extends AbstractProtocol {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(HttpServletProtocol.class);
    private final ServletContext servletContext;
    private SslContext sslContext;
    private DispatcherChannelHandler servletHandler;
    private long maxContentLength = 20 * 1024 * 1024;
    private int maxInitialLineLength = 40960;
    private int maxHeaderSize = 81920;
    private int maxChunkSize = 5 * 1024 * 1024;
    private int http2MaxReservedStreams = 100;
    private LogLevel http2Log;
    private boolean enableContentCompression = false;
    private int contentSizeThreshold = 8102;
    private String[] compressionMimeTypes = {"text/html", "text/xml", "text/plain",
            "text/css", "text/javascript", "application/javascript", "application/json",
            "application/xml"};
    private String[] compressionExcludedUserAgents = {};
    private boolean onServerStart = false;

    public HttpServletProtocol(ServletContext servletContext) {
        this(servletContext, null, null);
    }

    public HttpServletProtocol(ServletContext servletContext, Supplier<Executor> executorSupplier, Supplier<Executor> defaultExecutorSupplier) {
        this.servletContext = servletContext;
        if (defaultExecutorSupplier == null) {
            defaultExecutorSupplier = new LazyPool("NettyX-http");
        }
        servletContext.setAsyncExecutorSupplier(executorSupplier);
        servletContext.setDefaultExecutorSupplier(defaultExecutorSupplier);
        this.servletHandler = new DispatcherChannelHandler(executorSupplier);
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

    protected void configurableServletContext() throws Exception {
        if (servletContext.getResourceManager() == null) {
            servletContext.setDocBase(createTempDir("netty-docbase").getAbsolutePath());
        }
    }

    static File createTempDir(String prefix) {
        try {
            File tempDir = File.createTempFile(prefix + ".", "");
            tempDir.delete();
            tempDir.mkdir();
            tempDir.deleteOnExit();
            return tempDir;
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to create tempDir. java.io.tmpdir is set to "
                            + System.getProperty("java.io.tmpdir"),
                    ex);
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

    @Override
    public boolean canSupport(ByteBuf msg) {
        int protocolEndIndex = IOUtil.indexOf(msg, HttpConstants.LF);
        if (protocolEndIndex == -1 && msg.readableBytes() > 7) {
            // client multiple write packages. cause browser out of length.
            if (msg.getByte(0) == 'G'
                    && msg.getByte(1) == 'E'
                    && msg.getByte(2) == 'T'
                    && msg.getByte(3) == ' '
                    && msg.getByte(4) == '/') {
                return true;
            } else if (msg.getByte(0) == 'P'
                    && msg.getByte(1) == 'O'
                    && msg.getByte(2) == 'S'
                    && msg.getByte(3) == 'T'
                    && msg.getByte(4) == ' '
                    && msg.getByte(5) == '/') {
                return true;
            } else if (msg.getByte(0) == 'P'
                    && msg.getByte(1) == 'U'
                    && msg.getByte(2) == 'T'
                    && msg.getByte(3) == ' '
                    && msg.getByte(4) == '/') {
                return true;
            } else if (msg.getByte(0) == 'D'
                    && msg.getByte(1) == 'E'
                    && msg.getByte(2) == 'L'
                    && msg.getByte(3) == 'E'
                    && msg.getByte(4) == 'T'
                    && msg.getByte(5) == 'E'
                    && msg.getByte(6) == ' '
                    && msg.getByte(7) == '/') {
                return true;
            } else if (msg.getByte(0) == 'P'
                    && msg.getByte(1) == 'A'
                    && msg.getByte(2) == 'T'
                    && msg.getByte(3) == 'C'
                    && msg.getByte(4) == 'H'
                    && msg.getByte(5) == ' '
                    && msg.getByte(6) == '/') {
                return true;
            } else {
                return false;
            }
        } else if (protocolEndIndex < 9) {
            return false;
        } else if (msg.getByte(protocolEndIndex - 9) == 'H'
                && msg.getByte(protocolEndIndex - 8) == 'T'
                && msg.getByte(protocolEndIndex - 7) == 'T'
                && msg.getByte(protocolEndIndex - 6) == 'P') {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void addPipeline(Channel ch) throws Exception {
        super.addPipeline(ch);
        ChannelPipeline pipeline = ch.pipeline();

        if (isSsl()) {
            pipeline.addLast("SSL", newSslHandler(ch.alloc()));
            pipeline.addLast(new SslHttp2OrHttp11Handler());
        } else {
            pipeline.addLast(new Http2PrefaceOrHttpHandler());
        }
    }

    private void http2(ChannelPipeline pipeline, HttpScheme scheme) {
        pipeline.addLast(newHttp2Handler(scheme));
        http1(pipeline);
    }

    private void http1Upgrade(ChannelPipeline pipeline, HttpScheme scheme) {
        HttpServerCodec serverCodec = newHttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(serverCodec, newUpgradeCodecFactory(scheme), (int) maxContentLength);
        pipeline.addLast("h2upgrade", new CleartextHttp2ServerUpgradeHandler(serverCodec, upgradeHandler, newHttp2Handler(scheme)));
        http1(pipeline);
    }

    private void http1(ChannelPipeline pipeline) {
        pipeline.addLast("ContentDecompressor", newHttpContentDecompressor());

        //The content of compression
        if (enableContentCompression) {
            pipeline.addLast("ContentCompressor", newContentCompressor());
        }

        //Chunked transfer
        pipeline.addLast("ChunkedWrite", newChunkedWriteHandler());

        // ByteBuf to HttpContent
        pipeline.addLast(ByteBufToHttpContentChannelHandler.INSTANCE);

        //A business scheduler that lets the corresponding Servlet handle the request
        pipeline.addLast("Servlet", servletHandler);

        //Dynamic binding protocol for switching protocol
        DispatcherChannelHandler.setMessageToRunnable(pipeline.channel(), new NettyMessageToServletRunnable(servletContext, maxContentLength));
    }

    private Http2ConnectionHandler newHttp2Handler(HttpScheme scheme) {
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true, http2MaxReservedStreams);
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
                .propagateSettings(false)
                .validateHttpHeaders(true)
                .maxContentLength((int) maxContentLength)
                .build();

        HttpToHttp2ConnectionHandlerBuilder build = new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(listener)
                .connection(connection)
                .httpScheme(scheme);
        if (http2Log != null) {
            build.frameLogger(new Http2FrameLogger(http2Log));
        }
        return build.build();
    }

    private HttpServerUpgradeHandler.UpgradeCodecFactory newUpgradeCodecFactory(HttpScheme scheme) {
        return new HttpServerUpgradeHandler.UpgradeCodecFactory() {
            @Override
            public HttpServerUpgradeHandler.UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                    return new Http2ServerUpgradeCodec(newHttp2Handler(scheme));
                } else {
                    return null;
                }
            }
        };
    }

    public void setHttp2Log(LogLevel http2Log) {
        this.http2Log = http2Log;
    }

    public LogLevel getHttp2Log() {
        return http2Log;
    }

    public int getHttp2MaxReservedStreams() {
        return http2MaxReservedStreams;
    }

    public void setHttp2MaxReservedStreams(int http2MaxReservedStreams) {
        this.http2MaxReservedStreams = http2MaxReservedStreams;
    }

    private boolean isSsl() {
        return sslContext != null;
    }

    protected SslHandler newSslHandler(ByteBufAllocator allocator) {
        SSLEngine engine = sslContext.newEngine(allocator);
        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(sslParameters);
        return new SslHandler(engine, true);
    }

    protected HttpContentDecompressor newHttpContentDecompressor() {
        return new HttpContentDecompressor(false);
    }

    protected HttpServerCodec newHttpServerCodec() {
        return new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize, false);
    }

    protected ChunkedWriteHandler newChunkedWriteHandler() {
        return new ChunkedWriteHandler(this::getMaxBufferBytes);
    }

    protected HttpContentCompressor newContentCompressor() {
        return new HttpContentCompressor(6, 15, 8, contentSizeThreshold) {
            private ChannelHandlerContext ctx;

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                this.ctx = ctx;
                super.handlerAdded(ctx);
            }

            @Override
            protected Result beginEncode(HttpResponse response, String acceptEncoding) throws Exception {
                if (compressionExcludedUserAgents.length > 0) {
                    ServletHttpExchange httpExchange = ServletHttpExchange.getHttpExchange(ctx);
                    if (httpExchange != null) {
                        List<String> values = httpExchange.getRequest().getNettyHeaders().getAll(HttpHeaderConstants.USER_AGENT);
                        for (String excludedUserAgent : compressionExcludedUserAgents) {
                            for (String value : values) {
                                if (value.contains(excludedUserAgent)) {
                                    return null;
                                }
                            }
                        }
                    }
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
        };
    }

    public long getMaxBufferBytes() {
        return servletContext.getMaxBufferBytes();
    }

    public void setMaxBufferBytes(int maxBufferBytes) {
        servletContext.setMaxBufferBytes(maxBufferBytes);
    }

    public void setExecutor(Supplier<Executor> dispatcherExecutor) {
        this.servletHandler.setDispatcherExecutor(dispatcherExecutor);
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String getProtocolName() {
        String name = "http";
        if (sslContext != null) {
            name = name.concat("/https");
        }
        return name;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public void setSslContext(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public void setMaxContentLength(long maxContentLength) {
        this.maxContentLength = Math.toIntExact(maxContentLength);
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

    public void setCompressionExcludedUserAgents(String[] compressionExcludedUserAgents) {
        if (compressionExcludedUserAgents == null) {
            this.compressionExcludedUserAgents = new String[0];
        } else {
            this.compressionExcludedUserAgents = compressionExcludedUserAgents;
        }
    }

    class SslHttp2OrHttp11Handler extends ApplicationProtocolNegotiationHandler {
        SslHttp2OrHttp11Handler() {
            super(ApplicationProtocolNames.HTTP_1_1);
        }

        @Override
        public void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
            if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                http1(ctx.pipeline());
            } else if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                http2(ctx.pipeline(), HTTPS);
            } else {
                throw new IllegalStateException("Unknown protocol: " + protocol);
            }
        }
    }

    class Http2PrefaceOrHttpHandler extends ByteToMessageDecoder {
        private static final int PRI = 0x50524920;

        @Override
        protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
            if (in.readableBytes() < 4) {
                return;
            }
            if (in.getInt(in.readerIndex()) == PRI) {
                http2(ctx.pipeline(), HTTP);
            } else {
                http1Upgrade(ctx.pipeline(), HTTP);
            }
            ctx.pipeline().remove(this);
        }
    }

    static class LazyPool implements Supplier<Executor> {
        private volatile NettyThreadPoolExecutor executor;
        private final String poolName;

        LazyPool(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public NettyThreadPoolExecutor get() {
            if (executor == null) {
                synchronized (this) {
                    if (executor == null) {
                        int coreThreads = 2;
                        int maxThreads = 50;
                        int keepAliveSeconds = 180;
                        int priority = Thread.NORM_PRIORITY;
                        boolean daemon = false;
                        RejectedExecutionHandler handler = new HttpAbortPolicyWithReport(poolName, System.getProperty("user.home"), "Http Servlet");
                        executor = new NettyThreadPoolExecutor(
                                coreThreads, maxThreads, keepAliveSeconds, TimeUnit.SECONDS,
                                new SynchronousQueue<>(), poolName, priority, daemon, handler);
                    }
                }
            }
            return executor;
        }
    }
}
