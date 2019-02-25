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

    private ServletHttpServletRequest httpServletRequest;
    private ServletHttpServletResponse httpServletResponse;
    private ChannelHandlerContext channelHandlerContext;
    private ServletContext servletContext;
    private boolean isHttpKeepAlive;

    private ServletHttpObject() {
    }

    public static ServletHttpObject newInstance(ServletContext servletContext, ChannelHandlerContext context, FullHttpRequest fullHttpRequest) {
        ServletHttpObject instance = RECYCLER.getInstance();
        instance.servletContext = servletContext;
        instance.channelHandlerContext = context;
        instance.isHttpKeepAlive = HttpHeaderUtil.isKeepAlive(fullHttpRequest);

        //Create a new servlet request object
        instance.httpServletRequest = ServletHttpServletRequest.newInstance(instance,fullHttpRequest);
        //Create a new servlet response object
        instance.httpServletResponse = ServletHttpServletResponse.newInstance(instance);
        return instance;
    }

    public ServletHttpSession getSession(){
        return getSession(channelHandlerContext);
    }

    public void setSession(ServletHttpSession httpSession){
        setSession(channelHandlerContext,httpSession);
    }

    /**
     * Get httpSession from the properties bound in the pipe
     * @return
     */
    public static ServletHttpSession getSession(ChannelHandlerContext channelHandlerContext){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null) {
            Attribute<ServletHttpSession> attribute = channelHandlerContext.channel().attr(CHANNEL_ATTR_KEY_SESSION);
            if(attribute != null){
                return attribute.get();
            }
        }
        return null;
    }

    /**
     * Bind httpSession to the pipe property
     * @param httpSession
     */
    public static void setSession(ChannelHandlerContext channelHandlerContext, ServletHttpSession httpSession){
        if(isChannelActive(channelHandlerContext)) {
            channelHandlerContext.channel().attr(CHANNEL_ATTR_KEY_SESSION).set(httpSession);
        }
    }

    /**
     * Whether the pipe is active
     * @return
     */
    public static boolean isChannelActive(ChannelHandlerContext channelHandlerContext){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null && channelHandlerContext.channel().isActive()) {
            return true;
        }
        return false;
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

        httpServletResponse = null;
        httpServletRequest = null;
        channelHandlerContext = null;
        servletContext = null;

        RECYCLER.recycleInstance(this);
    }

}
