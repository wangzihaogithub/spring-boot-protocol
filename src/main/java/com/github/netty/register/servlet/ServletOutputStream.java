package com.github.netty.register.servlet;

import com.github.netty.register.servlet.util.HttpConstants;
import com.github.netty.register.servlet.util.HttpHeaderConstants;
import com.github.netty.core.util.*;
import com.github.netty.register.servlet.util.ServletUtil;
import com.github.netty.springboot.NettyProperties;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;

import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * servlet 输出流
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 * @author 84215
 */
@sun.misc.Contended
public class ServletOutputStream extends javax.servlet.ServletOutputStream implements Recyclable  {

    protected AtomicBoolean isEmpty = new AtomicBoolean(true);
    protected AtomicBoolean isClosed = new AtomicBoolean(false);
    protected AtomicBoolean isSendResponseHeader = new AtomicBoolean(false);

    private ServletHttpObject httpServletObject;
    private CompositeByteBufX buffer;
    private NettyProperties config;
    private Lock bufferReadWriterLock;
    private ChannelFutureListener closeListener;
    private ChannelFutureListener closeListenerWrapper = new CloseListenerRecycleWrapper();

    private static final AbstractRecycler<ServletOutputStream> RECYCLER = new AbstractRecycler<ServletOutputStream>() {
        @Override
        protected ServletOutputStream newInstance() {
            return new ServletOutputStream();
        }
    };

    protected ServletOutputStream() {}

    public static ServletOutputStream newInstance(ServletHttpObject httpServletObject) {
        ServletOutputStream instance = RECYCLER.getInstance();
        instance.setHttpServletObject(httpServletObject);
        instance.isEmpty.compareAndSet(true,false);
        return instance;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();
        if(len == 0){
            return;
        }

        try {
            CompositeByteBufX content = lockBuffer();
            if (content == null) {
                content = newContent();
                setBuffer(content);
            }
            ByteBuf ioByteBuf = allocByteBuf(content.alloc(), len);
            ioByteBuf.writeBytes(b, off, len);
            content.addComponent(ioByteBuf);
        }finally {
            unlockBuffer();
        }
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // TODO: 10月16日/0016 监听写入事件
    }

    public void setCloseListener(ChannelFutureListener closeListener) {
        this.closeListener = closeListener;
    }

    @Override
    public void write(byte[] b) throws IOException {
        checkClosed();
        write(b,0,b.length);
    }

    /**
     * 这里的int. 第三方框架都是按1个字节处理的, 不是4个字节
     * @param b
     * @throws IOException
     */
    @Override
    public void write(int b) throws IOException {
        checkClosed();
        int byteLen = 1;
        byte[] bytes = new byte[byteLen];
        IOUtil.setByte(bytes,0,b);
        write(bytes,0,byteLen);
    }

    @Override
    public void flush() throws IOException {
        checkClosed();
    }

    /**
     * 结束响应对象
     * 当响应被关闭时，容器必须立即刷出响应缓冲区中的所有剩余的内容到客户端。
     * 以下事件表明servlet满足了请求且响应对象即将关闭：
     * ■servlet的service方法终止。
     * ■响应的setContentLength或setContentLengthLong方法指定了大于零的内容量，且已经写入到响应。
     * ■sendError 方法已调用。
     * ■sendRedirect 方法已调用。
     * ■AsyncContext 的complete 方法已调用
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        if(isClosed()){
            return;
        }

        flush();
        if (isClosed.compareAndSet(false,true)) {
            try {
                CompositeByteBufX content = lockBuffer();
                if (content != null) {
                    httpServletObject.getHttpServletResponse()
                            .getNettyResponse()
                            .setContent(content);
                }
            }finally {
                unlockBuffer();
            }

            if (isSendResponseHeader.compareAndSet(false,true)) {
                sendResponse(getCloseListener());
            }
        }
    }

    /**
     * 发送响应
     * @param finishListener 操作完成后的监听
     */
    protected void sendResponse(ChannelFutureListener finishListener){
        //写入管道, 然后发送, 同时释放数据资源, 然后如果需要关闭则管理链接, 最后回调完成
        ChannelHandlerContext channel = httpServletObject.getChannelHandlerContext();
        ServletHttpServletRequest servletRequest = httpServletObject.getHttpServletRequest();
        ServletHttpServletResponse servletResponse = httpServletObject.getHttpServletResponse();
        NettyHttpResponse nettyResponse = servletResponse.getNettyResponse();
        ServletSessionCookieConfig sessionCookieConfig = httpServletObject.getServletContext().getSessionCookieConfig();
        boolean isKeepAlive = httpServletObject.isHttpKeepAlive();

        IOUtil.writerModeToReadMode(nettyResponse.content());
        settingResponseHeader(isKeepAlive, nettyResponse, servletRequest, servletResponse, sessionCookieConfig);
        writeResponseToChannel(isCloseChannel(isKeepAlive,nettyResponse.getStatus()), channel, nettyResponse, finishListener);
    }

    /**
     * 是否需要关闭管道
     * @param isKeepAlive
     * @param status
     * @return
     */
    private static boolean isCloseChannel(boolean isKeepAlive,HttpResponseStatus status){
        if(isKeepAlive){
            return false;
        }
        int responseStatus = status.code();
        if(responseStatus >= 100 && responseStatus < 200){
            return false;
        }
        return true;
    }

    /**
     * 检查是否关闭
     * @throws ClosedChannelException
     */
    protected void checkClosed() throws IOException {
        if(isClosed.get()){
            throw new IOException("Stream closed");
        }
    }

    /**
     * 创建新的缓冲区
     * @return
     */
    protected CompositeByteBufX newContent(){
        return new CompositeByteBufX();
    }

    /**
     * 分配缓冲区
     * @param allocator 分配器
     * @param len 需要的字节长度
     * @return
     */
    protected ByteBuf allocByteBuf(ByteBufAllocator allocator,int len){
        int maxHeapByteLength = config.getResponseWriterChunkMaxHeapByteLength();
        ByteBuf ioByteBuf;
        if(len > maxHeapByteLength){
            ioByteBuf = allocator.directBuffer(len);
        }else {
            ioByteBuf = allocator.heapBuffer(len);
        }
        return ioByteBuf;
    }

    /**
     * 销毁自己 ,
     */
    public void destroy(){
        this.httpServletObject = null;
        this.config = null;
        this.isSendResponseHeader = null;
        this.isClosed = null;
        this.buffer = null;
    }

    /**
     * 获取缓冲区 (带锁)
     * @return
     */
    public CompositeByteBufX lockBuffer(){
        if(bufferReadWriterLock == null){
            synchronized (this) {
                if(bufferReadWriterLock == null) {
                    bufferReadWriterLock = new ReentrantLock();
                }
            }
        }
        bufferReadWriterLock.lock();
        return buffer;
    }

    /**
     * 释放缓冲区的锁
     */
    public void unlockBuffer(){
        if(bufferReadWriterLock != null) {
            bufferReadWriterLock.unlock();
        }
    }

    /**
     * 获取缓冲区 (不带锁)
     * @return
     */
    protected CompositeByteBufX getBuffer() {
        return buffer;
    }

    /**
     * 放入缓冲区
     * @param buffer
     */
    protected void setBuffer(CompositeByteBufX buffer) {
        this.buffer = buffer;
    }

    /**
     * 重置缓冲区
     */
    protected void resetBuffer() {
        if (isClosed()) {
            return;
        }

        try {
            ByteBuf content = lockBuffer();
            if (content == null) {
                return;
            }
            if (content.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(content);
                setBuffer(null);
            }
        }finally {
            unlockBuffer();
        }
    }

    /**
     * 放入servlet对象
     * @param httpServletObject
     */
    protected void setHttpServletObject(ServletHttpObject httpServletObject) {
        this.httpServletObject = httpServletObject;
        this.config = httpServletObject.getConfig();
    }

    /**
     * 获取servlet对象
     * @return
     */
    protected ServletHttpObject getHttpServletObject() {
        return httpServletObject;
    }

    /**
     * 获取关闭监听
     * @return
     */
    protected ChannelFutureListener getCloseListener() {
        return closeListenerWrapper;
    }

    /**
     * 是否关闭
     * @return true=已关闭,false=未关闭
     */
    public boolean isClosed(){
        return isClosed.get();
    }

    /**
     * 写入响应
     * @param isCloseChannel
     * @param channel
     * @param nettyResponse
     * @param finishListener
     */
    private static void writeResponseToChannel(boolean isCloseChannel, ChannelHandlerContext channel,
                                        NettyHttpResponse nettyResponse, ChannelFutureListener finishListener) {
        ChannelPromise promise;
        //如果需要关闭管道 或者需要回调
        if(isCloseChannel || finishListener != null) {
            promise = channel.newPromise();
            promise.addListener(FlushListener.newInstance(isCloseChannel, finishListener));
        }else {
            promise = channel.voidPromise();
        }

        ByteBuf content = nettyResponse.content();
        if(content == null) {
            channel.writeAndFlush(nettyResponse, promise);
        }else {
            channel.write(nettyResponse,channel.voidPromise());
            channel.write(content,channel.voidPromise());
            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise);
        }
    }

    /**
     * 设置响应头
     * @param isKeepAlive 保持连接
     * @param nettyResponse netty响应
     * @param servletRequest servlet请求
     * @param servletResponse servlet响应
     * @param sessionCookieConfig sessionCookie配置
     */
    private static void settingResponseHeader(boolean isKeepAlive, NettyHttpResponse nettyResponse,
                                       ServletHttpServletRequest servletRequest, ServletHttpServletResponse servletResponse,
                                       ServletSessionCookieConfig sessionCookieConfig) {
        HttpHeaderUtil.setKeepAlive(nettyResponse, isKeepAlive);

        if (
//                !isKeepAlive &&
                        !HttpHeaderUtil.isContentLengthSet(nettyResponse)) {
            long contentLength = servletResponse.getContentLength();
            if(contentLength >= 0){
                HttpHeaderUtil.setContentLength(nettyResponse, contentLength);
            }else {
                ByteBuf content = nettyResponse.content();
                if(content != null) {
                    HttpHeaderUtil.setContentLength(nettyResponse, content.readableBytes());
                }
            }
        }

        String contentType = servletResponse.getContentType();
        String characterEncoding = servletResponse.getCharacterEncoding();
        List<Cookie> cookies = servletResponse.getCookies();

        HttpHeaders headers = nettyResponse.headers();
        if (null != contentType) {
            //Content Type 响应头的内容
            String value = (null == characterEncoding) ? contentType :
                    new StringBuilder(contentType)
                            .append(';')
                            .append(HttpHeaderConstants.CHARSET)
                            .append('=')
                            .append(characterEncoding).toString();

            headers.set(HttpHeaderConstants.CONTENT_TYPE, value);
        }
        // 时间日期响应头
        headers.set(HttpHeaderConstants.DATE, ServletUtil.newDateGMT());
        //服务器信息响应头
        String serverHeader = servletRequest.getServletContext().getServerHeader();
        if(serverHeader != null && serverHeader.length() > 0) {
            headers.set(HttpHeaderConstants.SERVER, serverHeader);
        }

        //语言
        Locale locale = servletResponse.getLocale();
        if(locale != null && !headers.contains(HttpHeaderConstants.CONTENT_LANGUAGE)){
            headers.set(HttpHeaderConstants.CONTENT_LANGUAGE,locale.toLanguageTag());
        }

        // cookies处理
        //先处理Session ，如果是新Session 并且 sessionId不是与请求传的sessionId一样, 需要通过Cookie写入
        ServletHttpSession httpSession = servletRequest.getSession(true);
        if (httpSession.isNew() && !httpSession.getId().equals(servletRequest.getRequestedSessionId())) {
            httpSession.setNewSessionFlag(false);
            String sessionCookieName = sessionCookieConfig.getName();
            if(StringUtil.isEmpty(sessionCookieName)){
                sessionCookieName = HttpConstants.JSESSION_ID_COOKIE;
            }
            Cookie cookie = new Cookie(sessionCookieName,servletRequest.getRequestedSessionId());
            cookie.setHttpOnly(true);
            if(sessionCookieConfig.getDomain() != null) {
                cookie.setDomain(sessionCookieConfig.getDomain());
            }
            if(sessionCookieConfig.getPath() == null) {
                cookie.setPath("/");
            }else {
                cookie.setPath(sessionCookieConfig.getPath());
            }
            cookie.setSecure(sessionCookieConfig.isSecure());
            if(sessionCookieConfig.getComment() != null) {
                cookie.setComment(sessionCookieConfig.getComment());
            }
            if(cookies == null) {
                cookies = RecyclableUtil.newRecyclableList(1);
                cookies.add(cookie);
            }
        }

        //其他业务或框架设置的cookie，逐条写入到响应头去
        if(cookies != null) {
            NettyHttpCookie nettyCookie = new NettyHttpCookie();
            for (Cookie cookie : cookies) {
                nettyCookie.wrap(ServletUtil.toNettyCookie(cookie));
                headers.add(HttpHeaderConstants.SET_COOKIE, ServletUtil.encodeCookie(nettyCookie));
            }
        }
    }

    @Override
    public void recycle() {
        try {
            if(isEmpty.get()){
                return;
            }
            close();
        } catch (IOException e) {
            ExceptionUtil.printRootCauseStackTrace(e);
        }
    }

    /**
     * 关闭监听的包装类 (用于回收数据)
     */
    private class CloseListenerRecycleWrapper implements ChannelFutureListener{
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if(closeListener != null) {
                closeListener.operationComplete(future);
                closeListener = null;
            }
            httpServletObject = null;
            config = null;
            if(buffer != null) {
                buffer = null;
            }
            isClosed.compareAndSet(true,false);
            isSendResponseHeader.compareAndSet(true,false);
            isEmpty.compareAndSet(false,true);
            RECYCLER.recycleInstance(ServletOutputStream.this);
        }
    }

    /**
     * 优化lambda实例数量, 减少gc次数
     */
    private static class FlushListener implements ChannelFutureListener,Recyclable {
        private boolean isCloseChannel;
        private ChannelFutureListener finishListener;

        private static final AbstractRecycler<FlushListener> RECYCLER = new AbstractRecycler<FlushListener>() {
            @Override
            protected FlushListener newInstance() {
                return new FlushListener();
            }
        };

        private static FlushListener newInstance(boolean isCloseChannel, ChannelFutureListener finishListener) {
            FlushListener instance = RECYCLER.getInstance();
            instance.isCloseChannel = isCloseChannel;
            instance.finishListener = finishListener;
            return instance;
        }

        @Override
        public void recycle() {
            finishListener = null;
            RECYCLER.recycleInstance(FlushListener.this);
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            try {
                if(isCloseChannel){
                    ChannelFuture channelFuture = future.channel().close();
                    if(finishListener != null) {
                        channelFuture.addListener(finishListener);
                    }
                }else {
                    if(finishListener != null) {
                        finishListener.operationComplete(future);
                    }
                }
            }catch (Throwable throwable){
                ExceptionUtil.printRootCauseStackTrace(throwable);
            }finally {
                FlushListener.this.recycle();
            }
        }

    }
}
