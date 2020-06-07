package com.github.netty.protocol;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.ChunkedWriteHandler;
import com.github.netty.core.util.IOUtil;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.servlet.*;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * HttpServlet protocol registry
 * @author wangzihao
 *  2018/11/11/011
 */
public class HttpServletProtocol extends AbstractProtocol {
    private static final LoggerX logger = LoggerFactoryX.getLogger(HttpServletProtocol.class);
    private final ServletContext servletContext;
    private SslContext sslContext;
    private SslContextBuilder sslContextBuilder;
    private ChannelHandler servletHandler;
    private int maxContentLength = 5 * 1024 * 1024;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxChunkSize = 5 * 1024 * 1024;
    /**
     * output stream maxBufferBytes
     * Each buffer accumulate the maximum number of bytes (default 1M)
     */
    private long maxBufferBytes = 1024 * 1024;
    private boolean enableContentCompression = false;
    private int contentSizeThreshold = 8102;
    private String[] compressionMimeTypes = {"text/html", "text/xml", "text/plain",
            "text/css", "text/javascript", "application/javascript", "application/json",
            "application/xml"};
    private String[] compressionExcludedUserAgents = {};

    public HttpServletProtocol(Supplier<Executor> executor, ServletContext servletContext){
        this.servletContext = servletContext;
        this.servletHandler = new ServletChannelHandler(servletContext,executor);
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        servletContext.setServerAddress(server.getServerAddress());
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextInitialized(new ServletContextEvent(servletContext));
        }

        //Servlet will be initialized automatically before use.
        initFilter(servletContext);

        listenerManager.onServletContainerInitializerStartup(Collections.emptySet(),servletContext);

        logger.info(
                "Netty servlet on port: {}, with context path '{}'",
                servletContext.getServerAddress().getPort(),
                servletContext.getContextPath()
                );
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
                    logger.error("destroyFilter error={},filter={}",e.toString(),filter,e);
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
                    logger.error("destroyServlet error={},servlet={}",e.toString(),servlet,e);
                }
            }
        }
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        int protocolEndIndex = IOUtil.indexOf(msg, HttpConstants.LF);
        if(protocolEndIndex < 9){
            return false;
        }

        if(msg.getByte(protocolEndIndex - 9) == 'H'
                && msg.getByte(protocolEndIndex - 8) == 'T'
                && msg.getByte(protocolEndIndex - 7) == 'T'
                &&  msg.getByte(protocolEndIndex - 6) == 'P'){
            return true;
        }
        return false;
    }

    @Override
    public void addPipeline(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslContextBuilder != null) {
            if(sslContext == null) {
                sslContext = sslContextBuilder.build();
            }
            SSLEngine engine = sslContext.newEngine(ch.alloc());
            pipeline.addLast("SSL", new SslHandler(engine,true));
        }

        pipeline.addLast("ContentDecompressor", new HttpContentDecompressor(false));

        //HTTP encoding decoding
        pipeline.addLast("HttpCodec", new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize, false));

        //HTTP request aggregation, set the maximum message value to 5M
        pipeline.addLast("Aggregator", new HttpObjectAggregator(maxContentLength,false));

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

        //Block transfer
        ChunkedWriteHandler chunkedWriteHandler = new ChunkedWriteHandler();
        chunkedWriteHandler.setMaxBufferBytes(maxBufferBytes);
        pipeline.addLast("ChunkedWrite",chunkedWriteHandler);

        //A business scheduler that lets the corresponding Servlet handle the request
        pipeline.addLast("Servlet", servletHandler);
    }

    public long getMaxBufferBytes() {
        return maxBufferBytes;
    }

    public void setMaxBufferBytes(long maxBufferBytes) {
        this.maxBufferBytes = maxBufferBytes;
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

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public void setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
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
}
