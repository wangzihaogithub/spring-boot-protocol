package com.github.netty.protocol;

import com.github.netty.core.AbstractProtocolsRegister;
import com.github.netty.core.util.IOUtil;
import com.github.netty.protocol.servlet.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * httpServlet协议注册器
 * @author acer01
 *  2018/11/11/011
 */
public class HttpServletProtocolsRegister extends AbstractProtocolsRegister {
    public static final int ORDER = 100;

    public static final String HANDLER_SSL = "SSL";
    public static final String HANDLER_AGGREGATOR = "Aggregator";
    public static final String HANDLER_SERVLET = "Servlet";
    public static final String HANDLER_HTTP_CODEC = "HttpCodec";

    /**
     * servlet上下文
     */
    private final ServletContext servletContext;
    /**
     * https 配置信息
     */
    private SslContext sslContext;
    private SslContextBuilder sslContextBuilder;
    private ChannelHandler servletHandler;

    private int maxContentLength = 5 * 1024 * 1024;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxChunkSize = 5 * 1024 * 1024;

    public HttpServletProtocolsRegister(Executor executor, ServletContext servletContext, SslContextBuilder sslContextBuilder){
        this.servletContext = servletContext;
        this.servletHandler = new ServletChannelHandler(servletContext,executor);
        this.sslContextBuilder = sslContextBuilder;
    }

    @Override
    public void onServerStart() throws Exception {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextInitialized(new ServletContextEvent(servletContext));
        }

        initFilter(servletContext);
        initServlet(servletContext);
    }

    @Override
    public void onServerStop() {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextDestroyed(new ServletContextEvent(servletContext));
        }

        destroyFilter();
        destroyServlet();
    }

    /**
     * 初始化过滤器
     * @param servletContext
     */
    protected void initFilter(ServletContext servletContext) throws ServletException {
        Map<String, ServletFilterRegistration> servletFilterRegistrationMap = servletContext.getFilterRegistrations();
        for(Map.Entry<String,ServletFilterRegistration> entry : servletFilterRegistrationMap.entrySet()){
            ServletFilterRegistration registration = entry.getValue();
            registration.getFilter().init(registration.getFilterConfig());
            registration.setInitParameter("_init","true");
        }
    }

    /**
     * 初始化servlet
     * @param servletContext
     */
    protected void initServlet(ServletContext servletContext) throws ServletException {
        Map<String, ServletRegistration> servletRegistrationMap = servletContext.getServletRegistrations();
        for(Map.Entry<String,ServletRegistration> entry : servletRegistrationMap.entrySet()){
            ServletRegistration registration = entry.getValue();
            registration.getServlet().init(registration.getServletConfig());
            registration.setInitParameter("_init","true");
        }
    }

    /**
     * 销毁过滤器
     */
    protected void destroyFilter(){
        Map<String, ServletFilterRegistration> servletRegistrationMap = servletContext.getFilterRegistrations();
        for(Map.Entry<String,ServletFilterRegistration> entry : servletRegistrationMap.entrySet()){
            ServletFilterRegistration registration = entry.getValue();
            Filter filter = registration.getFilter();
            if(filter == null) {
                continue;
            }
            String initFlag = registration.getInitParameter("_init");
            if(initFlag != null && "true".equals(initFlag)){
                filter.destroy();
            }
        }
    }

    /**
     * 销毁servlet
     */
    protected void destroyServlet(){
        Map<String, ServletRegistration> servletRegistrationMap = servletContext.getServletRegistrations();
        for(Map.Entry<String,ServletRegistration> entry : servletRegistrationMap.entrySet()){
            ServletRegistration registration = entry.getValue();
            Servlet servlet = registration.getServlet();
            if(servlet == null) {
                continue;
            }
            String initFlag = registration.getInitParameter("_init");
            if(initFlag != null && "true".equals(initFlag)){
                servlet.destroy();
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
    public void registerTo(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        //初始化SSL
        if (sslContextBuilder != null) {
            if(sslContext == null) {
                sslContext = sslContextBuilder.build();
            }
            SSLEngine engine = sslContext.newEngine(ch.alloc());
            pipeline.addLast(HANDLER_SSL, new SslHandler(engine,true));
        }

        //HTTP编码解码
        pipeline.addLast(HANDLER_HTTP_CODEC, new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize, false));

        //HTTP请求聚合，设置最大消息值为 5M
        pipeline.addLast(HANDLER_AGGREGATOR, new HttpObjectAggregator(maxContentLength));

        //内容压缩
//                    pipeline.addLast("ContentCompressor", new HttpContentCompressor());
//                pipeline.addLast("ContentDecompressor", new HttpContentDecompressor());

        //业务调度器, 让对应的Servlet处理请求
        pipeline.addLast(HANDLER_SERVLET, servletHandler);
    }

    @Override
    public int order() {
        return ORDER;
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
}
