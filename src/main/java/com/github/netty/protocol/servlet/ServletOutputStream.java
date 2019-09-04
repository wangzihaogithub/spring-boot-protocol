package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.FastThreadLocal;

import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servlet OutputStream
 * @author wangzihao
 */
public class ServletOutputStream extends javax.servlet.ServletOutputStream implements Recyclable  {
    private static final FastThreadLocal<DateFormat> DATE_FORMAT_GMT_LOCAL = new FastThreadLocal<DateFormat>() {
        private TimeZone timeZone = TimeZone.getTimeZone("GMT");
        @Override
        protected DateFormat initialValue() {
            DateFormat df = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.ENGLISH);
            df.setTimeZone(timeZone);
            return df;
        }
    };
    public static final String APPEND_CONTENT_TYPE = ";" + HttpHeaderConstants.CHARSET + "=";
    private static final Recycler<ServletOutputStream> RECYCLER = new Recycler<>(ServletOutputStream::new);

    protected AtomicBoolean isEmpty = new AtomicBoolean(true);
    protected AtomicBoolean isClosed = new AtomicBoolean(false);
    protected AtomicBoolean isSendResponseHeader = new AtomicBoolean(false);
    private ServletHttpObject httpServletObject;
    private CompositeByteBufX buffer;
    private Lock bufferReadWriterLock;
    private ChannelFutureListener closeListener;
    private ChannelFutureListener closeListenerWrapper = new CloseListenerRecycleWrapper();
    private int responseWriterChunkMaxHeapByteLength;

    protected ServletOutputStream() {}

    public static ServletOutputStream newInstance(ServletHttpObject httpServletObject) {
        ServletOutputStream instance = RECYCLER.getInstance();
        instance.setHttpServletObject(httpServletObject);
        instance.responseWriterChunkMaxHeapByteLength = httpServletObject.getServletContext().getResponseWriterChunkMaxHeapByteLength();

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
            lock();
            CompositeByteBufX content = getBuffer();
            if (content == null) {
                content = newContent();
                setBuffer(content);
            }
            ByteBuf ioByteBuf = allocByteBuf(content.alloc(), len);
            ioByteBuf.writeBytes(b, off, len);
            content.addComponent(ioByteBuf);
        }finally {
            unlock();
        }
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // TODO: 10-16/0016 Listen for write events
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
     * Int. Third-party frameworks are all 1 byte, not 4 bytes
     * @param b byte
     * @throws IOException IOException
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
     * End response object
     * The following events indicate that the servlet has satisfied the request and the response object is about to close
     * The following events indicate that the servlet has satisfied the request and the response object is about to close：
     * ■The service method of the servlet terminates.
     * ■The setContentLength or setContentLengthLong method of the response specifies an internal capacity greater than zero, and has already been written to the response.
     * ■sendError Method called。
     * ■sendRedirect Method called。
     * ■AsyncContext.complete Method called
     * @throws IOException IOException
     */
    @Override
    public void close() throws IOException {
        if(isClosed()){
            return;
        }

        flush();
        if (isClosed.compareAndSet(false,true)) {
            try {
                lock();
                CompositeByteBufX content = getBuffer();
                if (content != null) {
                    httpServletObject.getHttpServletResponse()
                            .getNettyResponse()
                            .setContent(content);
                }
            }finally {
                unlock();
            }

            if (isSendResponseHeader.compareAndSet(false,true)) {
                sendResponse(getCloseListener());
            }
        }
    }

    /**
     * Send a response
     * @param finishListener Monitoring after the operation is completed
     */
    protected void sendResponse(ChannelFutureListener finishListener){
        //Write the pipe, send it, release the data resource at the same time, then manage the link if it needs to be closed, and finally the callback completes
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
     * Whether the pipe needs to be closed
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
     * Check if it's closed
     * @throws ClosedChannelException
     */
    protected void checkClosed() throws IOException {
        if(isClosed.get()){
            throw new IOException("Stream closed");
        }
    }

    /**
     * Create a new buffer
     * @return CompositeByteBufX
     */
    protected CompositeByteBufX newContent(){
        return new CompositeByteBufX();
    }

    /**
     * Allocation buffer
     * @param allocator allocator distributor
     * @param len The required byte length
     * @return ByteBuf
     */
    protected ByteBuf allocByteBuf(ByteBufAllocator allocator,int len){
        ByteBuf ioByteBuf;
        if(len > responseWriterChunkMaxHeapByteLength){
            ioByteBuf = allocator.directBuffer(len);
        }else {
            ioByteBuf = allocator.heapBuffer(len);
        }
        return ioByteBuf;
    }

    /**
     * Destroy yourself,
     */
    public void destroy(){
        this.httpServletObject = null;
        this.isSendResponseHeader = null;
        this.isClosed = null;
        this.buffer = null;
    }

    /**
     * lock buffer
     */
    public void lock(){
        if(bufferReadWriterLock == null){
            synchronized (this) {
                if(bufferReadWriterLock == null) {
                    bufferReadWriterLock = new ReentrantLock();
                }
            }
        }
        bufferReadWriterLock.lock();
    }

    /**
     * Releases the lock on the buffer
     */
    public void unlock(){
        if(bufferReadWriterLock != null) {
            bufferReadWriterLock.unlock();
        }
    }

    /**
     * Get buffer
     * @return CompositeByteBufX
     */
    protected CompositeByteBufX getBuffer() {
        return buffer;
    }

    /**
     * Set buffer
     * @param buffer CompositeByteBufX buffer
     */
    protected void setBuffer(CompositeByteBufX buffer) {
        this.buffer = buffer;
    }

    /**
     * Reset buffer
     */
    protected void resetBuffer() {
        if (isClosed()) {
            return;
        }

        try {
            lock();
            if(RecyclableUtil.release(getBuffer())) {
                this.buffer = null;
            }
        }finally {
            unlock();
        }
    }

    /**
     * Put in the servlet object
     * @param httpServletObject httpServletObject
     */
    protected void setHttpServletObject(ServletHttpObject httpServletObject) {
        this.httpServletObject = httpServletObject;
    }

    /**
     * Get servlet object
     * @return ServletHttpObject
     */
    protected ServletHttpObject getHttpServletObject() {
        return httpServletObject;
    }

    /**
     * Get off listening
     * @return ChannelFutureListener ChannelFutureListener
     */
    protected ChannelFutureListener getCloseListener() {
        return closeListenerWrapper;
    }

    /**
     * Whether to shut down
     * @return True = closed,false= not closed
     */
    public boolean isClosed(){
        return isClosed.get();
    }

    /**
     * Written response
     * @param isCloseChannel isCloseChannel
     * @param channel channel
     * @param nettyResponse nettyResponse
     * @param finishListener finishListener
     */
    private static void writeResponseToChannel(boolean isCloseChannel, ChannelHandlerContext channel,
                                        NettyHttpResponse nettyResponse, ChannelFutureListener finishListener) {
        ChannelPromise promise;
        //If you need to close the pipe or need a callback
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
            channel.write(new DefaultHttpContent(content),channel.voidPromise());
            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise);
        }
    }

    /**
     * Set the response header
     * @param isKeepAlive keep alive
     * @param nettyResponse nettyResponse
     * @param servletRequest servletRequest
     * @param servletResponse servletResponse
     * @param sessionCookieConfig sessionCookieConfig
     */
    private static void settingResponseHeader(boolean isKeepAlive, NettyHttpResponse nettyResponse,
                                       ServletHttpServletRequest servletRequest, ServletHttpServletResponse servletResponse,
                                       ServletSessionCookieConfig sessionCookieConfig) {
        HttpHeaderUtil.setKeepAlive(nettyResponse, isKeepAlive);
        HttpHeaders headers = nettyResponse.headers();

        //Content length
        if (!headers.contains(HttpHeaderConstants.CONTENT_LENGTH)) {
            long contentLength = servletResponse.getContentLength();
            if(contentLength >= 0){
                headers.set(HttpHeaderConstants.CONTENT_LENGTH, contentLength);
            }else {
                ByteBuf content = nettyResponse.content();
                if(content != null) {
                    headers.set(HttpHeaderConstants.CONTENT_LENGTH, content.readableBytes());
                }
            }
        }

        // Time and date response header
        headers.set(HttpHeaderConstants.DATE, DATE_FORMAT_GMT_LOCAL.get().format(new Date()));

        //Content Type The content of the response header
        String contentType = servletResponse.getContentType();
        if (null != contentType) {
            String characterEncoding = servletResponse.getCharacterEncoding();
            String value = (null == characterEncoding) ? contentType :
                    RecyclableUtil.newStringBuilder()
                            .append(contentType)
                            .append(APPEND_CONTENT_TYPE)
                            .append(characterEncoding).toString();
            headers.set(HttpHeaderConstants.CONTENT_TYPE, value);
        }

        //Server information response header
        String serverHeader = servletRequest.getServletContext().getServerHeader();
        if(serverHeader != null && serverHeader.length() > 0) {
            headers.set(HttpHeaderConstants.SERVER, serverHeader);
        }

        //language
        Locale locale = servletResponse.getLocale();
        if(!headers.contains(HttpHeaderConstants.CONTENT_LANGUAGE)){
            headers.set(HttpHeaderConstants.CONTENT_LANGUAGE,locale.toLanguageTag());
        }

        // Cookies processing
        //Session is handled first. If it is a new Session and the Session id is not the same as the Session id passed by the request, it needs to be written through the Cookie
        List<Cookie> cookies = servletResponse.getCookies();
        ServletHttpSession httpSession = servletRequest.getSession(false);
        if (httpSession != null && httpSession.isNew()
//		        && !httpSession.getId().equals(servletRequest.getRequestedSessionId())
        ) {
            String sessionCookieName = sessionCookieConfig.getName();
            if(sessionCookieName == null || sessionCookieName.isEmpty()){
                sessionCookieName = HttpConstants.JSESSION_ID_COOKIE;
            }
            String sessionCookiePath = sessionCookieConfig.getPath();
            if(sessionCookiePath == null || sessionCookiePath.isEmpty()) {
                sessionCookiePath = HttpConstants.DEFAULT_SESSION_COOKIE_PATH;
            }
            String sessionCookieText = ServletUtil.encodeCookie(sessionCookieName,servletRequest.getRequestedSessionId(), -1,
                    sessionCookiePath,sessionCookieConfig.getDomain(),sessionCookieConfig.isSecure(),Boolean.TRUE);
            headers.add(HttpHeaderConstants.SET_COOKIE, sessionCookieText);

            httpSession.setNewSessionFlag(false);
        }

        //Cookies set by other businesses or frameworks are written to the response header one by one
        int cookieSize = cookies.size();
        if(cookieSize > 0) {
            for (int i=0; i<cookieSize; i++) {
                Cookie cookie = cookies.get(i);
                String cookieText = ServletUtil.encodeCookie(cookie.getName(),cookie.getValue(),cookie.getMaxAge(),cookie.getPath(),cookie.getDomain(),cookie.getSecure(),cookie.isHttpOnly());
                headers.add(HttpHeaderConstants.SET_COOKIE, cookieText);
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
            e.printStackTrace();
        }
    }

    /**
     * Closing the listening wrapper class (for data collection)
     */
    private class CloseListenerRecycleWrapper implements ChannelFutureListener{
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if(closeListener != null) {
                closeListener.operationComplete(future);
                closeListener = null;
            }
            httpServletObject = null;
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
     * FlushListener Optimize the number of lambda instances to reduce gc times
     */
    private static class FlushListener implements ChannelFutureListener,Recyclable {
        private boolean isCloseChannel;
        private ChannelFutureListener finishListener;

        private static final Recycler<FlushListener> RECYCLER = new Recycler<>(FlushListener::new);

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
                throwable.printStackTrace();
            }finally {
                FlushListener.this.recycle();
            }
        }
    }
}
