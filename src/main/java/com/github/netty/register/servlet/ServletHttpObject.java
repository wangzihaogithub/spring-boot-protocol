package com.github.netty.register.servlet;

import com.github.netty.springboot.NettyProperties;
import com.github.netty.core.util.AbstractRecycler;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.HttpHeaderUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * servlet对象 (包含3大对象 : 请求, 响应, tcp通道 )
 *
 * @author acer01
 *  2018/8/1/001
 */
public class ServletHttpObject implements Recyclable{

    private static final AbstractRecycler<ServletHttpObject> RECYCLER = new AbstractRecycler<ServletHttpObject>() {
        @Override
        protected ServletHttpObject newInstance() {
            return new ServletHttpObject();
        }
    };
    private static final AttributeKey<ServletHttpSession> CHANNEL_ATTR_KEY_SESSION = AttributeKey.valueOf(ServletHttpSession.class + "#ServletHttpSession");

    private ServletHttpServletRequest httpServletRequest;
    private ServletHttpServletResponse httpServletResponse;
    private ChannelHandlerContext channelHandlerContext;
    private ServletContext servletContext;
    private NettyProperties config;
    private boolean isHttpKeepAlive;

    private ServletHttpObject() {
    }

    public static ServletHttpObject newInstance(ServletContext servletContext, NettyProperties config, ChannelHandlerContext context, FullHttpRequest fullHttpRequest) {
        ServletHttpObject instance = RECYCLER.getInstance();
        instance.servletContext = servletContext;
        instance.config = config;
        instance.channelHandlerContext = context;
        instance.isHttpKeepAlive = HttpHeaderUtil.isKeepAlive(fullHttpRequest);

        //创建新的servlet请求对象
        instance.httpServletRequest = ServletHttpServletRequest.newInstance(instance,fullHttpRequest);
        //创建新的servlet响应对象
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
     * 从管道中绑定的属性中获取 httpSession
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
     * 把 httpSession绑定到管道属性中
     * @param httpSession
     */
    public static void setSession(ChannelHandlerContext channelHandlerContext, ServletHttpSession httpSession){
        if(isChannelActive(channelHandlerContext)) {
            channelHandlerContext.channel().attr(CHANNEL_ATTR_KEY_SESSION).set(httpSession);
        }
    }

    /**
     * 管道是否处于活动状态
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
        return servletContext.getServletServerAddress();
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

    public NettyProperties getConfig() {
        return config;
    }

    /**
     * 回收servlet对象
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
