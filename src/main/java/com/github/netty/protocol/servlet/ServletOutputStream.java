package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.PlatformDependent;

import javax.servlet.WriteListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Servlet OutputStream
 *
 * @author wangzihao
 */
public class ServletOutputStream extends javax.servlet.ServletOutputStream implements Recyclable, NettyOutputStream {
    private static final Recycler<ServletOutputStream> RECYCLER = new Recycler<>(ServletOutputStream::new);
    public static final ServletResetBufferIOException RESET_BUFFER_EXCEPTION = new ServletResetBufferIOException();

    private int responseWriterChunkMaxHeapByteLength;
    private ChannelProgressivePromise blockPromise;
    private final CloseListener closeListenerWrapper = new CloseListener();

    protected ServletHttpExchange servletHttpExchange;
    protected WriteListener writeListener;
    protected ChannelProgressivePromise lastContentPromise;
    protected final AtomicLong writeBytes = new AtomicLong();
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);
    protected final AtomicBoolean isSendResponse = new AtomicBoolean(false);

    protected ServletOutputStream() {
    }

    public static ServletOutputStream newInstance(ServletHttpExchange servletHttpExchange) {
        ServletOutputStream instance = RECYCLER.getInstance();
        instance.blockPromise = null;
        instance.setServletHttpExchange(servletHttpExchange);
        instance.writeBytes.set(0);
        instance.responseWriterChunkMaxHeapByteLength = servletHttpExchange.getServletContext().getResponseWriterChunkMaxHeapByteLength();
        instance.isSendResponse.set(false);
        instance.isClosed.set(false);
        return instance;
    }

    public long getWriteBytes() {
        return writeBytes.get();
    }

    @Override
    public ChannelProgressivePromise write(ByteBuffer httpBody) throws IOException {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(httpBody);
        return writeHttpBody(byteBuf, byteBuf.readableBytes());
    }

    @Override
    public ChannelProgressivePromise write(ByteBuf httpBody) throws IOException {
        IOUtil.writerModeToReadMode(httpBody);
        return writeHttpBody(httpBody, httpBody.readableBytes());
    }

    @Override
    public ChannelProgressivePromise write(ChunkedInput input) throws IOException {
        return writeHttpBody(input, input.length());
    }

    @Override
    public ChannelProgressivePromise write(FileChannel fileChannel, long position, long count) throws IOException {
        return writeHttpBody(new DefaultFileRegion(fileChannel, position, count), count);
    }

    @Override
    public ChannelProgressivePromise write(File file, long position, long count) throws IOException {
        return writeHttpBody(new DefaultFileRegion(file, position, count), count);
    }

    @Override
    public ChannelProgressivePromise write(File httpBody) throws IOException {
        long length = httpBody.length();
        return writeHttpBody(new DefaultFileRegion(httpBody, 0, length), length);
    }

    protected ChannelProgressivePromise writeHttpBody(Object httpBody, long length) throws IOException {
        checkClosed();
        writeResponseHeaderIfNeed();
        ServletHttpExchange servletHttpExchange = this.servletHttpExchange;
        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ChannelProgressivePromise promise = context.newProgressivePromise();

        if (length > 0) {
            writeBytes.addAndGet(length);
        }

        long contentLength = servletHttpExchange.getResponse().getContentLength();
        // response finish
        if (contentLength >= 0 && writeBytes.get() >= contentLength) {
            boolean autoFlush = servletHttpExchange.getServletContext().isAutoFlush();
            if (httpBody instanceof ByteBuf) {
                DefaultLastHttpContent httpContent = new DefaultLastHttpContent((ByteBuf) httpBody, false);
                if (autoFlush) {
                    context.write(httpContent, promise);
                } else {
                    context.writeAndFlush(httpContent, promise);
                }
            } else {
                context.write(httpBody);
                if (autoFlush) {
                    context.write(LastHttpContent.EMPTY_LAST_CONTENT, promise);
                } else {
                    context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise);
                }
            }
            lastContentPromise = promise;
        } else {
            // Response continues
            context.write(httpBody, promise);
        }

        // limiting control. avoid buffer memory overflow.
        blockIfNeed(promise);
        return promise;
    }

    /**
     * limiting control. avoid buffer memory overflow.
     *
     * @param promise last write promise
     * @throws IOException wrap ClosedChannelException or other channel exception
     */
    private void blockIfNeed(ChannelProgressivePromise promise) throws IOException {
        ServletHttpExchange exchange = this.servletHttpExchange;
        ChannelHandlerContext context = exchange.getChannelHandlerContext();
        long pendingWriteBytes = exchange.getPendingWriteBytes();
        if (pendingWriteBytes <= 0) {
            return;
        }
        if (context.executor().inEventLoop()) {
            // 1 time slices
            Thread.yield();
            context.flush();
            Thread.yield();
            ChannelUtils.forceFlush(context.channel());
        } else {
            int bufferSize = exchange.getResponse().getBufferSize();
            ChannelProgressivePromise blockPromise = this.blockPromise;
            boolean requiresFlush = true;
            if (pendingWriteBytes >= bufferSize && blockPromise == null) {
                context.flush();
                requiresFlush = false;
                // record promise
                this.blockPromise = blockPromise = promise;
            }

            // Over the double buffer size, block the processing
            int doubleBufferSize = bufferSize << 1;
            if (pendingWriteBytes >= doubleBufferSize) {
                try {
                    if (blockPromise == null) {
                        blockPromise = promise;
                    }
                    if (requiresFlush) {
                        context.flush();
                    }
                    blockPromise.sync();
                } catch (InterruptedException ignored) {
                } catch (Exception e) {
                    throw new IOException("flush fail = " + e, e);
                } finally {
                    this.blockPromise = null;
                }
            }
        }
    }

    private void writeResponseHeaderIfNeed() {
        if (isSendResponse.compareAndSet(false, true)) {
            ServletHttpServletResponse servletResponse = servletHttpExchange.getResponse();
            NettyHttpResponse nettyResponse = servletResponse.getNettyResponse();
            ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
            context.write(nettyResponse);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();
        if (len == 0) {
            return;
        }

        ChannelHandlerContext context = servletHttpExchange.getChannelHandlerContext();
        ByteBuf ioByteBuf = allocByteBuf(context.alloc(), len);
        ioByteBuf.writeBytes(b, off, len);
        IOUtil.writerModeToReadMode(ioByteBuf);

        writeHttpBody(ioByteBuf, ioByteBuf.readableBytes());
    }

    @Override
    public boolean isReady() {
        ServletHttpExchange exchange = this.servletHttpExchange;
        if (exchange == null) {
            return true;
        }

        long pendingWriteBytes = exchange.getPendingWriteBytes();
        if (pendingWriteBytes <= 0) {
            return true;
        }
        boolean ready = true;
        if (!exchange.getChannelHandlerContext().executor().inEventLoop()) {
            // limiting control
            // pendingWriteBytes < exchange.getResponse().getBufferSize() * 2
            ready = pendingWriteBytes < exchange.getResponse().getBufferSize() << 1;
        }
        return ready;
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
        write(b, 0, b.length);
    }

    /**
     * Int. Third-party frameworks are all 1 byte, not 4 bytes
     *
     * @param b byte
     * @throws IOException IOException
     */
    @Override
    public void write(int b) throws IOException {
        checkClosed();
        int byteLen = 1;
        byte[] bytes = new byte[byteLen];
        IOUtil.setByte(bytes, 0, b);
        write(bytes, 0, byteLen);
    }

    @Override
    public void flush() throws IOException {
        checkClosed();
        writeResponseHeaderIfNeed();
        ServletHttpExchange exchange = this.servletHttpExchange;
        if (exchange != null && !exchange.getServletContext().isAutoFlush()) {
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
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            ChannelFuture closeFuture = lastContentPromise;
            if (closeFuture == null) {
                ServletHttpExchange exchange = getServletHttpExchange();
                ChannelHandlerContext context = exchange.getChannelHandlerContext();
                writeResponseHeaderIfNeed();

                if (exchange.getServletContext().isAutoFlush()) {
                    closeFuture = context.write(LastHttpContent.EMPTY_LAST_CONTENT);
                } else {
                    closeFuture = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                }
                closeFuture.addListener(closeListenerWrapper);
            } else if (closeFuture.isDone()) {
                try {
                    closeListenerWrapper.operationComplete(closeFuture);
                } catch (Exception e) {
                    PlatformDependent.throwException(e);
                }
            } else {
                closeFuture.addListener(closeListenerWrapper);
            }
        }
    }

    /**
     * Check if it's closed
     *
     * @throws ClosedChannelException
     */
    protected void checkClosed() throws IOException {
        if (isClosed()) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Allocation buffer
     *
     * @param allocator allocator distributor
     * @param len       The required byte length
     * @return ByteBuf
     */
    protected ByteBuf allocByteBuf(ByteBufAllocator allocator, int len) {
        ByteBuf ioByteBuf;
        if (len > responseWriterChunkMaxHeapByteLength && NettyUtil.freeDirectMemory() > len) {
            ioByteBuf = allocator.directBuffer(len);
        } else {
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
        if (context != null) {
            ChunkedWriteHandler handler = (ChunkedWriteHandler) context.handler();
            int unWriteSize = handler.unWriteSize();
            if (unWriteSize > 0) {
                handler.discard(RESET_BUFFER_EXCEPTION);
            }
        }
        writeBytes.set(0);
    }

    /**
     * Put in the servlet object
     *
     * @param servletHttpExchange servletHttpExchange
     */
    protected void setServletHttpExchange(ServletHttpExchange servletHttpExchange) {
        this.servletHttpExchange = servletHttpExchange;
    }

    /**
     * Get servlet object
     *
     * @return ServletHttpExchange
     */
    protected ServletHttpExchange getServletHttpExchange() {
        return servletHttpExchange;
    }

    /**
     * Whether to shut down
     *
     * @return True = closed,false= not closed
     */
    public boolean isClosed() {
        return isClosed.get();
    }

    @Override
    public <T> void recycle(Consumer<T> consumer) {
        if (isClosed()) {
            return;
        }
        this.closeListenerWrapper.addRecycleConsumer(consumer);
        close();
    }

    /**
     * Closing the listening wrapper class (for data collection)
     */
    public class CloseListener implements ChannelFutureListener {
        private ChannelFutureListener closeListener;
        private final Queue<Consumer> recycleConsumerQueue = new LinkedList<>();

        public void addRecycleConsumer(Consumer consumer) {
            recycleConsumerQueue.add(consumer);
        }

        public void setCloseListener(ChannelFutureListener closeListener) {
            this.closeListener = closeListener;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            ChannelFutureListener closeListener = this.closeListener;
            if (closeListener != null) {
                closeListener.operationComplete(future);
            }
            Consumer recycleConsumer;
            while ((recycleConsumer = recycleConsumerQueue.poll()) != null) {
                recycleConsumer.accept(ServletOutputStream.this);
            }
            blockPromise = null;
            lastContentPromise = null;
            writeListener = null;
            servletHttpExchange = null;
            this.closeListener = null;
            RECYCLER.recycleInstance(ServletOutputStream.this);
        }
    }

}
