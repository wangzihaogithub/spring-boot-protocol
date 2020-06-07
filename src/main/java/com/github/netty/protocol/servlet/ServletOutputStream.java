package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.PlatformDependent;

import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Servlet OutputStream
 * @author wangzihao
 */
public class ServletOutputStream extends javax.servlet.ServletOutputStream implements Recyclable,NettyOutputStream {
    public static final String APPEND_CONTENT_TYPE = ";" + HttpHeaderConstants.CHARSET + "=";
    private static final Recycler<ServletOutputStream> RECYCLER = new Recycler<>(ServletOutputStream::new);

    protected AtomicBoolean isClosed = new AtomicBoolean(false);
    private ServletHttpExchange servletHttpExchange;
    private WriteListener writeListener;
    private CloseListener closeListenerWrapper = new CloseListener();
    private int responseWriterChunkMaxHeapByteLength;
    protected AtomicBoolean isSendResponse = new AtomicBoolean(false);
    protected AtomicBoolean isSettingResponse = new AtomicBoolean(false);

    protected ServletOutputStream() {}

    public static ServletOutputStream newInstance(ServletHttpExchange servletHttpExchange) {
        ServletOutputStream instance = RECYCLER.getInstance();
        instance.setServletHttpExchange(servletHttpExchange);
        instance.responseWriterChunkMaxHeapByteLength = servletHttpExchange.getServletContext().getResponseWriterChunkMaxHeapByteLength();
        instance.isSendResponse.set(false);
        instance.isSettingResponse.set(false);
        instance.isClosed.set(false);
        return instance;
    }

    @Override
    public ChannelProgressivePromise write(ChunkedInput input) throws IOException {
        checkClosed();

        writeResponseHeaderIfNeed();

        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ChannelProgressivePromise promise = context.newProgressivePromise();
        context.write(input,promise);
        return promise;
    }

    @Override
    public ChannelProgressivePromise write(FileChannel fileChannel, long position, long count) throws IOException {
        checkClosed();

        writeResponseHeaderIfNeed();

        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ChannelProgressivePromise promise = context.newProgressivePromise();
        context.write(new DefaultFileRegion(fileChannel,position,count),promise);
        return promise;
    }

    @Override
    public ChannelProgressivePromise write(File file, long position, long count) throws IOException {
        checkClosed();

        writeResponseHeaderIfNeed();

        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ChannelProgressivePromise promise = context.newProgressivePromise();
        context.write(new DefaultFileRegion(file,position,count),promise);
        return promise;
    }


    private void writeResponseHeaderIfNeed(){
        if(isSendResponse.compareAndSet(false,true)){
            ServletHttpServletResponse servletResponse = servletHttpExchange.getResponse();
            NettyHttpResponse nettyResponse = servletResponse.getNettyResponse();
            ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
            context.write(nettyResponse);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();
        if(len == 0){
            return;
        }

        writeResponseHeaderIfNeed();

        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ByteBuf ioByteBuf = allocByteBuf(context.alloc(), len);
        ioByteBuf.writeBytes(b, off, len);
        IOUtil.writerModeToReadMode(ioByteBuf);

        context.write(ioByteBuf);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        this.writeListener = writeListener;
    }

    public void setCloseListener(ChannelFutureListener closeListener) {
        this.closeListenerWrapper.setCloseListener(closeListener);
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
        flush0();
    }

    protected void flush0(){
        writeResponseHeaderIfNeed();
        if(isSettingResponse.compareAndSet(false,true)){
            settingResponse();
        }
        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        context.flush();
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
     */
    @Override
    public void close() throws IOException{
        if(isClosed()){
            return;
        }

        if(isClosed.compareAndSet(false,true)){
            flush0();
            ServletHttpExchange exchange = getServletHttpExchange();
            ChannelHandlerContext context = exchange.getChannelHandlerContext();

            context.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                    .addListener(closeListenerWrapper);
        }
    }

    /**
     * Send a response
     */
    protected void settingResponse(){
        //Write the pipe, send it, release the data resource at the same time, then manage the link if it needs to be closed, and finally the callback completes
        ServletHttpServletRequest servletRequest = servletHttpExchange.getRequest();
        ServletHttpServletResponse servletResponse = servletHttpExchange.getResponse();
        NettyHttpResponse nettyResponse = servletResponse.getNettyResponse();
        ServletSessionCookieConfig sessionCookieConfig = servletHttpExchange.getServletContext().getSessionCookieConfig();
        boolean isKeepAlive = servletHttpExchange.isHttpKeepAlive();

        IOUtil.writerModeToReadMode(nettyResponse.content());
        settingResponseHeader(isKeepAlive, nettyResponse, servletRequest, servletResponse, sessionCookieConfig);
    }

    /**
     * Whether the pipe needs to be closed
     * @param isKeepAlive
     * @param responseStatus
     * @return
     */
    private static boolean isCloseChannel(boolean isKeepAlive,int responseStatus){
        if(isKeepAlive){
            return false;
        }
        if(responseStatus >= 100 && responseStatus < 300){
            return false;
        }
        return true;
    }

    /**
     * Check if it's closed
     * @throws ClosedChannelException
     */
    protected void checkClosed() throws IOException {
        if(isClosed()){
            throw new IOException("Stream closed");
        }
    }

    /**
     * Allocation buffer
     * @param allocator allocator distributor
     * @param len The required byte length
     * @return ByteBuf
     */
    protected ByteBuf allocByteBuf(ByteBufAllocator allocator, int len){
        ByteBuf ioByteBuf;
        if(len > responseWriterChunkMaxHeapByteLength){
            if(PlatformDependent.usedDirectMemory() + len >= PlatformDependent.maxDirectMemory() * 0.8F){
                ioByteBuf = allocator.heapBuffer(len);
            }else {
                ioByteBuf = allocator.directBuffer(len);
            }
        }else {
            ioByteBuf = allocator.heapBuffer(len);
        }
        return ioByteBuf;
    }

    /**
     * Destroy yourself,
     */
    public void destroy(){
        this.servletHttpExchange = null;
        this.isClosed = null;
    }

    /**
     * Reset buffer
     */
    protected void resetBuffer() {
        if (isClosed()) {
            return;
        }
        ServletHttpExchange exchange = getServletHttpExchange();
        ChannelHandlerContext channelHandlerContext = exchange.getChannelHandlerContext();
        ChannelHandlerContext context = channelHandlerContext.pipeline().context(ChunkedWriteHandler.class);
        if(context != null) {
            ChunkedWriteHandler handler = (ChunkedWriteHandler) context.handler();
            int unWriteSize = handler.unWriteSize();
            if(unWriteSize > 0) {
                handler.discard(new ServletResetBufferIOException());
            }
        }
    }

    /**
     * Put in the servlet object
     * @param servletHttpExchange servletHttpExchange
     */
    protected void setServletHttpExchange(ServletHttpExchange servletHttpExchange) {
        this.servletHttpExchange = servletHttpExchange;
    }

    /**
     * Get servlet object
     * @return ServletHttpExchange
     */
    protected ServletHttpExchange getServletHttpExchange() {
        return servletHttpExchange;
    }

    /**
     * Get off listening
     * @return ChannelFutureListener ChannelFutureListener
     */
    protected CloseListener getCloseListener() {
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
        long contentLength = servletResponse.getContentLength();
        if(contentLength >= 0) {
            headers.remove(HttpHeaderConstants.TRANSFER_ENCODING);
            headers.set(HttpHeaderConstants.CONTENT_LENGTH, contentLength);
        }else {
            nettyResponse.enableTransferEncodingChunked();
        }

        // Time and date response header
        if(!headers.contains(HttpHeaderConstants.DATE)) {
            headers.set(HttpHeaderConstants.DATE, ServletUtil.getDateByRfcHttp());
        }

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
    public <T> void recycle(Consumer<T> consumer) {
        this.closeListenerWrapper.addRecycleConsumer(consumer);
        if(isClosed()){
            return;
        }
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closing the listening wrapper class (for data collection)
     */
    public class CloseListener implements ChannelFutureListener {
        private ChannelFutureListener closeListener;
        private final Queue<Consumer> recycleConsumerQueue = new LinkedList<>();

        public void addRecycleConsumer(Consumer consumer){
            recycleConsumerQueue.add(consumer);
        }

        public void setCloseListener(ChannelFutureListener closeListener) {
            this.closeListener = closeListener;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean isNeedClose = isCloseChannel(servletHttpExchange.isHttpKeepAlive(),
                    servletHttpExchange.getResponse().getStatus());
            if(isNeedClose){
                Channel channel = future.channel();
                if(channel.isActive()){
                    ChannelFuture closeFuture = channel.close();
                    ChannelFutureListener closeListener = this.closeListener;
                    if(closeListener != null) {
                        closeFuture.addListener(closeListener);
                    }
                }else {
                    ChannelFutureListener closeListener = this.closeListener;
                    if(closeListener != null) {
                        closeListener.operationComplete(future);
                    }
                }
            }

            Consumer recycleConsumer;
            while ((recycleConsumer = recycleConsumerQueue.poll()) != null){
                recycleConsumer.accept(ServletOutputStream.this);
            }

            writeListener = null;
            servletHttpExchange = null;
            this.closeListener = null;
            RECYCLER.recycleInstance(ServletOutputStream.this);
        }
    }

}
