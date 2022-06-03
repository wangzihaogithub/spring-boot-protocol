package com.github.netty.protocol;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.DispatcherChannelHandler;
import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.*;
import com.github.netty.protocol.servlet.util.HttpAbortPolicyWithReport;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;

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

/**
 * HttpServlet protocol registry
 * @author wangzihao
 *  2018/11/11/011
 */
public class HttpServletProtocol extends AbstractProtocol {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(HttpServletProtocol.class);
    private final ServletContext servletContext;
    private SslContext sslContext;
    private SslContextBuilder sslContextBuilder;
    private DispatcherChannelHandler servletHandler;
    private long maxContentLength = 20 * 1024 * 1024;
    private int maxInitialLineLength = 40960;
    private int maxHeaderSize = 81920;
    private int maxChunkSize = 5 * 1024 * 1024;
    private boolean enableContentCompression = false;
    private int contentSizeThreshold = 8102;
    private String[] compressionMimeTypes = {"text/html", "text/xml", "text/plain",
            "text/css", "text/javascript", "application/javascript", "application/json",
            "application/xml"};
    private String[] compressionExcludedUserAgents = {};
    private boolean onServerStart = false;

    public HttpServletProtocol(ServletContext servletContext) {
        this(servletContext,null,null);
    }

    public HttpServletProtocol(ServletContext servletContext, Supplier<Executor> executorSupplier, Supplier<Executor> defaultExecutorSupplier){
        this.servletContext = servletContext;
        if(defaultExecutorSupplier == null){
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
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextInitialized(new ServletContextEvent(servletContext));
        }

        //Servlet will be initialized automatically before use.
        initFilter(servletContext);
        listenerManager.onServletDefaultInitializer(servletContext.getDefaultServlet(), servletContext);

        listenerManager.onServletContainerInitializerStartup(Collections.emptySet(),servletContext);
        this.onServerStart = true;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStop(T server) {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextDestroyed(new ServletContextEvent(servletContext));
        }

        destroyFilter();
        destroyServlet();
    }

    protected void configurableServletContext() throws Exception {
        if(servletContext.getResourceManager() == null) {
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
        }catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to create tempDir. java.io.tmpdir is set to "
                            + System.getProperty("java.io.tmpdir"),
                    ex);
        }
    }

    /**
     * Initialization filter
     * @param servletContext servletContext
     */
    protected void initFilter(ServletContext servletContext) throws ServletException {
        Map<String, ServletFilterRegistration> servletFilterRegistrationMap = servletContext.getFilterRegistrations();
        for(ServletFilterRegistration registration : servletFilterRegistrationMap.values()){
            if(registration.isInitFilterCas(false,true)){
                registration.getFilter().init(registration.getFilterConfig());
            }
        }
    }

    /**
     * Destruction filter
     */
    protected void destroyFilter(){
        Map<String, ServletFilterRegistration> servletRegistrationMap = servletContext.getFilterRegistrations();
        for(ServletFilterRegistration registration : servletRegistrationMap.values()){
            Filter filter = registration.getFilter();
            if(filter == null) {
                continue;
            }
            if(registration.isInitFilter()){
                try {
                    filter.destroy();
                }catch (Exception e){
                    LOGGER.error("destroyFilter error={},filter={}",e.toString(),filter,e);
                }
            }
        }
    }

    /**
     * Destruction servlet
     */
    protected void destroyServlet(){
        Map<String, ServletRegistration> servletRegistrationMap = servletContext.getServletRegistrations();
        for(ServletRegistration registration : servletRegistrationMap.values()){
            Servlet servlet = registration.getServlet();
            if(servlet == null) {
                continue;
            }
            if(registration.isInitServlet()){
                try {
                    servlet.destroy();
                }catch (Exception e){
                    LOGGER.error("destroyServlet error={},servlet={}",e.toString(),servlet,e);
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
        if (sslContextBuilder != null) {
            if (sslContext == null) {
                sslContext = sslContextBuilder.build();
            }
            SSLEngine engine = sslContext.newEngine(ch.alloc());
            SSLParameters sslParameters = engine.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(sslParameters);
            pipeline.addLast("SSL", new SslHandler(engine, true));
        }

        pipeline.addLast("ContentDecompressor", new HttpContentDecompressor(false));

        //HTTP encoding decoding
        pipeline.addLast("HttpCodec", new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize, false));

        //HTTP request aggregation, set the maximum message value to 5M
//        pipeline.addLast("Aggregator", new HttpObjectAggregator((int) maxContentLength,false));

        //The content of compression
        if(enableContentCompression) {
            pipeline.addLast("ContentCompressor", new HttpContentCompressor(6,15, 8, contentSizeThreshold){
                private ChannelHandlerContext ctx;

                @Override
                public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                    this.ctx = ctx;
                    super.handlerAdded(ctx);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    if (msg instanceof ByteBuf) {
                        // convert ByteBuf to HttpContent to make it work with compression. This is needed as we use the
                        // ChunkedWriteHandler to send files when compression is enabled.
                        msg = new DefaultHttpContent((ByteBuf) msg);
                    }
                    super.write(ctx, msg, promise);
                }

                @Override
                protected Result beginEncode(HttpResponse response, String acceptEncoding) throws Exception {
                    if(compressionExcludedUserAgents.length > 0) {
                        ServletHttpExchange httpExchange = ServletHttpExchange.getHttpExchange(ctx);
                        if(httpExchange != null) {
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

                    if(compressionMimeTypes.length > 0) {
                        List<String> values = response.headers().getAll(HttpHeaderConstants.CONTENT_TYPE);
                        for (String mimeType : compressionMimeTypes) {
                            for(String value : values){
                                if(value.contains(mimeType)){
                                    return super.beginEncode(response, acceptEncoding);
                                }
                            }
                        }
                    }
                    return null;
                }
            });
        }

        //Chunked transfer
        pipeline.addLast("ChunkedWrite",new ChunkedWriteHandler(this::getMaxBufferBytes));

        //A business scheduler that lets the corresponding Servlet handle the request
        pipeline.addLast("Servlet", servletHandler);

        //Dynamic binding protocol for switching protocol
        DispatcherChannelHandler.setMessageToRunnable(ch, new NettyMessageToServletRunnable(servletContext, maxContentLength));
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
        if(sslContextBuilder != null){
            name = name.concat("/https");
        }
        return name;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public SslContextBuilder getSslContextBuilder() {
        return sslContextBuilder;
    }

    public void setSslContextBuilder(SslContextBuilder sslContextBuilder) {
        this.sslContextBuilder = sslContextBuilder;
    }

    public void setMaxContentLength(long maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public void setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public void setMaxChunkSize(long maxChunkSize) {
        if(maxChunkSize != (int)maxChunkSize){
            this.maxChunkSize = Integer.MAX_VALUE;
        }else {
            this.maxChunkSize = (int) maxChunkSize;
        }
    }

    public void setCompressionMimeTypes(String[] compressionMimeTypes) {
        if(compressionMimeTypes == null){
            this.compressionMimeTypes = new String[0];
        }else {
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
        if(compressionExcludedUserAgents == null){
            this.compressionExcludedUserAgents = new String[0];
        }else {
            this.compressionExcludedUserAgents = compressionExcludedUserAgents;
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
            if(executor == null){
                synchronized (this){
                    if(executor == null){
                        int coreThreads = 2;
                        int maxThreads = 50;
                        int keepAliveSeconds = 180;
                        int priority = Thread.NORM_PRIORITY;
                        boolean daemon = false;
                        RejectedExecutionHandler handler = new HttpAbortPolicyWithReport(poolName, System.getProperty("user.home"),"Http Servlet");
                        executor = new NettyThreadPoolExecutor(
                                coreThreads,maxThreads,keepAliveSeconds, TimeUnit.SECONDS,
                                new SynchronousQueue<>(),poolName,priority,daemon,handler);
                    }
                }
            }
            return executor;
        }
    }
}
