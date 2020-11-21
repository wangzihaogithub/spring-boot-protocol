package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;

import javax.servlet.WriteListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Servlet OutputStream
 * @author wangzihao
 */
public class ServletOutputStream extends javax.servlet.ServletOutputStream implements Recyclable, NettyOutputStream {
    private static final Recycler<ServletOutputStream> RECYCLER = new Recycler<>(ServletOutputStream::new);
//    private static final Lock ALLOC_DIRECT_BUFFER_LOCK = new ReentrantLock();
    public static final ServletResetBufferIOException RESET_BUFFER_EXCEPTION = new ServletResetBufferIOException();

    protected AtomicBoolean isClosed = new AtomicBoolean(false);
    protected ServletHttpExchange servletHttpExchange;
    protected WriteListener writeListener;
    private CloseListener closeListenerWrapper = new CloseListener();
    private int responseWriterChunkMaxHeapByteLength;
    protected AtomicBoolean isSendResponse = new AtomicBoolean(false);
    private boolean flush = false;
    private boolean write = false;

    protected ServletOutputStream() {}

    public static ServletOutputStream newInstance(ServletHttpExchange servletHttpExchange) {
        ServletOutputStream instance = RECYCLER.getInstance();
        instance.setServletHttpExchange(servletHttpExchange);
        instance.responseWriterChunkMaxHeapByteLength = servletHttpExchange.getServletContext().getResponseWriterChunkMaxHeapByteLength();
        instance.isSendResponse.set(false);
        instance.isClosed.set(false);
        return instance;
    }

    public boolean isWrite() {
        return write;
    }

    public boolean isFlush() {
        return flush;
    }

    @Override
    public ChannelProgressivePromise write(ByteBuffer httpBody) throws IOException {
        return writeHttpBody(Unpooled.wrappedBuffer(httpBody));
    }

    @Override
    public ChannelProgressivePromise write(ByteBuf httpBody) throws IOException {
        return writeHttpBody(httpBody);
    }

    @Override
    public ChannelProgressivePromise write(ChunkedInput input) throws IOException {
        return writeHttpBody(input);
    }

    @Override
    public ChannelProgressivePromise write(FileChannel fileChannel, long position, long count) throws IOException {
        return writeHttpBody(new DefaultFileRegion(fileChannel,position,count));
    }

    @Override
    public ChannelProgressivePromise write(File file, long position, long count) throws IOException {
        return writeHttpBody(new DefaultFileRegion(file,position,count));
    }

    @Override
    public ChannelProgressivePromise write(File httpBody) throws IOException {
        return writeHttpBody(new DefaultFileRegion(httpBody,0,httpBody.length()));
    }

    protected ChannelProgressivePromise writeHttpBody(Object httpBody) throws IOException {
        checkClosed();
        writeResponseHeaderIfNeed();
        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ChannelProgressivePromise promise = context.newProgressivePromise();
        context.write(httpBody,promise);
        this.write = true;
        this.flush = false;
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

        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ByteBuf ioByteBuf = allocByteBuf(context.alloc(), len);
        ioByteBuf.writeBytes(b, off, len);
        IOUtil.writerModeToReadMode(ioByteBuf);

        writeHttpBody(ioByteBuf);
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
        writeResponseHeaderIfNeed();
        if(!flush) {
            ServletHttpExchange exchange = this.servletHttpExchange;
            if (exchange != null && !exchange.getServletContext().isAutoFlush()) {
                exchange.getChannelHandlerContext().flush();
                flush = true;
            }
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
    public void close() {
        if(isClosed()){
            return;
        }

        if(isClosed.compareAndSet(false,true)){
            ServletHttpExchange exchange = getServletHttpExchange();
            ChannelHandlerContext context = exchange.getChannelHandlerContext();
            writeResponseHeaderIfNeed();
            context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
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
        if(len > responseWriterChunkMaxHeapByteLength && NettyUtil.freeDirectMemory() > len){
            ioByteBuf = allocator.directBuffer(len);
//            ALLOC_DIRECT_BUFFER_LOCK.lock();
//            try {
//                if (NettyUtil.freeDirectMemory() > len) {
//                    ioByteBuf = allocator.directBuffer(len);
//                } else {
//                    ioByteBuf = allocator.heapBuffer(len);
//                }
//            }finally {
//                ALLOC_DIRECT_BUFFER_LOCK.unlock();
//            }
        }else {
            ioByteBuf = allocator.heapBuffer(len);
        }
        return ioByteBuf;
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
                handler.discard(RESET_BUFFER_EXCEPTION);
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
        close();
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

        private void callListener(){
            Consumer recycleConsumer;
            while ((recycleConsumer = recycleConsumerQueue.poll()) != null) {
                recycleConsumer.accept(ServletOutputStream.this);
            }
        }
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean isNeedClose = isCloseChannel(servletHttpExchange.isHttpKeepAlive(),
                    servletHttpExchange.getResponse().getStatus());
            ChannelFuture closeFuture = null;
            if(isNeedClose){
                Channel channel = future.channel();
                if(channel.isActive()){
                    closeFuture = channel.close();
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

            if(closeFuture != null) {
                closeFuture.addListener(f -> callListener());
            }else {
                callListener();
            }

            write = false;
            flush = false;
            writeListener = null;
            servletHttpExchange = null;
            this.closeListener = null;
            RECYCLER.recycleInstance(ServletOutputStream.this);
        }
    }

}
