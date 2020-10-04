package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.PlatformDependent;

import javax.servlet.WriteListener;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Servlet OutputStream
 * @author wangzihao
 */
public class ServletOutputStream extends javax.servlet.ServletOutputStream implements Recyclable, NettyOutputStream {
    private static final Recycler<ServletOutputStream> RECYCLER = new Recycler<>(ServletOutputStream::new);
    private static final Lock ALLOC_DIRECT_BUFFER_LOCK = new ReentrantLock();
    private static final float THRESHOLD = SystemPropertyUtil.getFloat("netty-servlet.directBufferThreshold",0.8F);
    protected AtomicBoolean isClosed = new AtomicBoolean(false);
    private ServletHttpExchange servletHttpExchange;
    private WriteListener writeListener;
    private CloseListener closeListenerWrapper = new CloseListener();
    private int responseWriterChunkMaxHeapByteLength;
    protected AtomicBoolean isSendResponse = new AtomicBoolean(false);

    protected ServletOutputStream() {}

    public static ServletOutputStream newInstance(ServletHttpExchange servletHttpExchange) {
        ServletOutputStream instance = RECYCLER.getInstance();
        instance.setServletHttpExchange(servletHttpExchange);
        instance.responseWriterChunkMaxHeapByteLength = servletHttpExchange.getServletContext().getResponseWriterChunkMaxHeapByteLength();
        instance.isSendResponse.set(false);
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

    protected void flush0() throws IOException {
        writeResponseHeaderIfNeed();
        ServletHttpExchange exchange = this.servletHttpExchange;
        if(exchange != null) {
            exchange.getResponse().getNettyResponse().flush();
            exchange.getChannelHandlerContext().flush();
        }
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
    public void close() throws IOException {
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
        if(len > responseWriterChunkMaxHeapByteLength && PlatformDependent.usedDirectMemory() + len < PlatformDependent.maxDirectMemory() * THRESHOLD){
            ALLOC_DIRECT_BUFFER_LOCK.lock();
            try {
                if (PlatformDependent.usedDirectMemory() + len < PlatformDependent.maxDirectMemory() * THRESHOLD) {
                    ioByteBuf = allocator.directBuffer(len);
                } else {
                    ioByteBuf = allocator.heapBuffer(len);
                }
            }finally {
                ALLOC_DIRECT_BUFFER_LOCK.unlock();
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
