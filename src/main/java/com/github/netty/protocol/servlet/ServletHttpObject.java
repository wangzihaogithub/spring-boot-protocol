package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recycler;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.HttpHeaderUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Servlet object (contains 3 big objects: request, response, TCP channel)
 * @author wangzihao
 *  2018/8/1/001
 */
public class ServletHttpObject implements Recyclable{
    private static final Recycler<ServletHttpObject> RECYCLER = new Recycler<>(ServletHttpObject::new);
    private static final AttributeKey<ServletHttpSession> CHANNEL_ATTR_KEY_SESSION = AttributeKey.valueOf(ServletHttpSession.class + "#ServletHttpSession");
    private static final AttributeKey<ServletHttpObject> CHANNEL_ATTR_KEY_OBJECT = AttributeKey.valueOf(ServletHttpObject.class + "#ServletHttpObject");

    private ServletHttpServletRequest httpServletRequest;
    private ServletHttpServletResponse httpServletResponse;
    private ChannelHandlerContext channelHandlerContext;
    private ServletContext servletContext;
    private boolean isHttpKeepAlive;

    private ServletHttpObject() {
    }

    public static ServletHttpObject newInstance(ServletContext servletContext, ChannelHandlerContext context, FullHttpRequest fullHttpRequest) {
        ServletHttpObject instance = RECYCLER.getInstance();
        setHttpObject(context,instance);
        instance.servletContext = servletContext;
        instance.channelHandlerContext = context;
        instance.isHttpKeepAlive = HttpHeaderUtil.isKeepAlive(fullHttpRequest);

        //Create a new servlet request object
        instance.httpServletRequest = ServletHttpServletRequest.newInstance(instance,fullHttpRequest);
        //Create a new servlet response object
        instance.httpServletResponse = ServletHttpServletResponse.newInstance(instance);
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

    public static ServletHttpObject getHttpObject(ChannelHandlerContext channelHandlerContext){
        return getAttribute(channelHandlerContext,CHANNEL_ATTR_KEY_OBJECT);
    }

    public static void setHttpObject(ChannelHandlerContext channelHandlerContext, ServletHttpObject httpObject){
        setAttribute(channelHandlerContext,CHANNEL_ATTR_KEY_OBJECT,httpObject);
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

    public ServletHttpServletRequest getHttpServletRequest() {
        return httpServletRequest;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public ServletHttpServletResponse getHttpServletResponse() {
        return httpServletResponse;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    public InetSocketAddress getServletServerAddress(){
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

    /**
     * Recycle servlet object
     */
    @Override
    public void recycle() {
        httpServletResponse.recycle();
        httpServletRequest.recycle();

        if(channelHandlerContext instanceof Recyclable){
            ((Recyclable) channelHandlerContext).recycle();
        }

        if(channelHandlerContext != null) {
            setAttribute(channelHandlerContext, CHANNEL_ATTR_KEY_OBJECT, null);
        }
        httpServletResponse = null;
        httpServletRequest = null;
        channelHandlerContext = null;
        servletContext = null;

        RECYCLER.recycleInstance(this);
    }

}
