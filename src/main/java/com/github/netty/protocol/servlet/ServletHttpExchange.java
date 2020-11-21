package com.github.netty.protocol.servlet;

import com.github.netty.protocol.servlet.util.HttpHeaderUtil;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Servlet object (contains 3 big objects: request, response, TCP channel)
 * @author wangzihao
 *  2018/8/1/001
 */
public class ServletHttpExchange implements Recyclable,AutoCloseable{
    private static final Recycler<ServletHttpExchange> RECYCLER = new Recycler<>(ServletHttpExchange::new);
    private static final AttributeKey<ServletHttpSession> CHANNEL_ATTR_KEY_SESSION = AttributeKey.valueOf(ServletHttpSession.class + "#ServletHttpSession");
    private static final AttributeKey<ServletHttpExchange> CHANNEL_ATTR_KEY_EXCHANGE = AttributeKey.valueOf(ServletHttpExchange.class + "#ServletHttpExchange");

    private ServletHttpServletRequest request;
    private ServletHttpServletResponse response;
    private ChannelHandlerContext channelHandlerContext;
    private ServletContext servletContext;
    private boolean isHttpKeepAlive;
    private final AtomicInteger close = new AtomicInteger(CLOSE_NO);
    public static final int CLOSE_NO = 0;
    public static final int CLOSE_ING = 1;
    public static final int CLOSE_YES = 2;

    private ServletHttpExchange() {
    }

    public static ServletHttpExchange newInstance(ServletContext servletContext, ChannelHandlerContext context, HttpRequest httpRequest) {
        ServletHttpExchange instance = RECYCLER.getInstance();
        setHttpExchange(context,instance);

        instance.close.set(CLOSE_NO);
        instance.servletContext = servletContext;
        instance.channelHandlerContext = context;
        instance.isHttpKeepAlive = HttpHeaderUtil.isKeepAlive(httpRequest);

        //Create a new servlet request object
        instance.request = ServletHttpServletRequest.newInstance(instance,httpRequest);
        //Create a new servlet response object
        instance.response = ServletHttpServletResponse.newInstance(instance);
        return instance;
    }

    /**
     * Get httpSession from the properties bound in the pipe
     * @param channelHandlerContext channelHandlerContext
     * @return ServletHttpSession
     */
    public static ServletHttpSession getHttpSession(ChannelHandlerContext channelHandlerContext){
        return getAttribute(channelHandlerContext,CHANNEL_ATTR_KEY_SESSION);
    }

    /**
     * Bind httpSession to the pipe property
     * @param channelHandlerContext channelHandlerContext
     * @param httpSession httpSession
     */
    public static void setHttpSession(ChannelHandlerContext channelHandlerContext, ServletHttpSession httpSession){
        setAttribute(channelHandlerContext,CHANNEL_ATTR_KEY_SESSION,httpSession);
    }

    /**
     * Whether the pipe is active
     * @param channelHandlerContext channelHandlerContext
     * @return boolean isChannelActive
     */
    public static boolean isChannelActive(ChannelHandlerContext channelHandlerContext){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null && channelHandlerContext.channel().isActive()) {
            return true;
        }
        return false;
    }

    public static ServletHttpExchange getHttpExchange(ChannelHandlerContext channelHandlerContext){
        return getAttribute(channelHandlerContext, CHANNEL_ATTR_KEY_EXCHANGE);
    }

    public static void setHttpExchange(ChannelHandlerContext channelHandlerContext, ServletHttpExchange httpExchange){
        setAttribute(channelHandlerContext, CHANNEL_ATTR_KEY_EXCHANGE,httpExchange);
    }

    public ServletHttpSession getHttpSession(){
        return getHttpSession(channelHandlerContext);
    }

    public void setHttpSession(ServletHttpSession httpSession){
        setHttpSession(channelHandlerContext,httpSession);
    }

    public boolean isHttpKeepAlive() {
        return isHttpKeepAlive;
    }

    public ServletHttpServletRequest getRequest() {
        return request;
    }

    public void touch(Object hit){
        if(request != null){
            ByteBuf byteBuf = request.getInputStream0().unwrap();
            if(byteBuf != null){
                byteBuf.touch(hit);
            }
        }
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public ServletHttpServletResponse getResponse() {
        return response;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    public InetSocketAddress getServerAddress(){
        return servletContext.getServerAddress();
    }

    public InetSocketAddress getLocalAddress(){
        SocketAddress socketAddress = channelHandlerContext.channel().localAddress();
        if(socketAddress == null){
            return null;
        }
        if(socketAddress instanceof InetSocketAddress){
            return (InetSocketAddress) socketAddress;
        }
        return null;
    }

    public InetSocketAddress getRemoteAddress(){
        SocketAddress socketAddress = channelHandlerContext.channel().remoteAddress();
        if(socketAddress == null){
            return null;
        }
        if(socketAddress instanceof InetSocketAddress){
            return (InetSocketAddress) socketAddress;
        }
        return null;
    }

    public static <T> T getAttribute(ChannelHandlerContext channelHandlerContext,AttributeKey<T> key){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null) {
            Attribute<T> attribute = channelHandlerContext.channel().attr(key);
            if(attribute != null){
                return attribute.get();
            }
        }
        return null;
    }

    public static <T> void setAttribute(ChannelHandlerContext context, AttributeKey<T> key,T value){
        if(isChannelActive(context)) {
            context.channel().attr(key).set(value);
        }
    }

    public <T> T getAttribute(AttributeKey<T> key){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null) {
            Attribute<T> attribute = channelHandlerContext.channel().attr(key);
            if(attribute != null){
                return attribute.get();
            }
        }
        return null;
    }

    public <T> void setAttribute(AttributeKey<T> key,T value){
        if(isChannelActive(channelHandlerContext)) {
            channelHandlerContext.channel().attr(key).set(value);
        }
    }

    /**
     * Recycle servlet object
     */
    @Override
    public void recycle() {
        if(close.compareAndSet(CLOSE_NO,CLOSE_ING)) {
            response.recycle(recycleCallback);
        }
    }

    @Override
    public void close() {
        recycle();
    }

    private final Consumer<Object> recycleCallback = e ->{
        request.recycle();

        if(channelHandlerContext instanceof Recyclable){
            ((Recyclable) channelHandlerContext).recycle();
        }

        if(channelHandlerContext != null) {
            setAttribute(channelHandlerContext, CHANNEL_ATTR_KEY_EXCHANGE, null);
        }
        response = null;
        request = null;
        servletContext = null;
        RECYCLER.recycleInstance(this);
        close.set(CLOSE_YES);
    };

    public int closeStatus() {
        return close.get();
    }

}
