package com.github.netty.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.ReferenceCountUtil;

import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A {@link ChannelHandler} that adds support for writing a large data stream
 * asynchronously neither spending a lot of memory nor getting
 * {@link OutOfMemoryError}.  Large data streaming such as file
 * transfer requires complicated state management in a {@link ChannelHandler}
 * implementation.  {@link ChunkedWriteHandler} manages such complicated states
 * so that you can send a large data stream without difficulties.
 * <p>
 * To use {@link ChunkedWriteHandler} in your application, you have to insert
 * a new {@link ChunkedWriteHandler} instance:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("streamer", <b>new {@link ChunkedWriteHandler}()</b>);
 * p.addLast("handler", new MyHandler());
 * </pre>
 * Once inserted, you can write a {@link ChunkedInput} so that the
 * {@link ChunkedWriteHandler} can pick it up and fetch the content of the
 * stream chunk by chunk and write the fetched chunk downstream:
 * <pre>
 * {@link Channel} ch = ...;
 * ch.write(new {@link io.netty.handler.stream.ChunkedFile}(new File("video.mkv"));
 * </pre>
 *
 * <h3>Sending a stream which generates a chunk intermittently</h3>
 * <p>
 * Some {@link ChunkedInput} generates a chunk on a certain event or timing.
 * Such {@link ChunkedInput} implementation often returns {@code null} on
 * {@link ChunkedInput#readChunk(ByteBufAllocator)}, resulting in the indefinitely suspended
 * transfer.  To resume the transfer when a new chunk is available, you have to
 * call {@link #resumeTransfer()}.
 */
public class ChunkedWriteHandler extends ChannelDuplexHandler {
    private static final LoggerX LOGGER =
            LoggerFactoryX.getLogger(ChunkedWriteHandler.class);
    private final Queue<PendingWrite> queue;
    private volatile ChannelHandlerContext ctx;
    private final AtomicBoolean flushIng = new AtomicBoolean(false);
    /**
     * output stream maxBufferBytes
     * Each buffer accumulate the maximum number of bytes (default 1M)
     */
    private LongSupplier maxBufferBytes;
    private final AtomicLong unFlushBytes = new AtomicLong();

    public ChunkedWriteHandler(LongSupplier maxBufferBytes) {
        this(new ArrayDeque<PendingWrite>(),maxBufferBytes);
    }

    public ChunkedWriteHandler(Queue<PendingWrite> queue,LongSupplier maxBufferBytes) {
        this.queue = queue;
        this.maxBufferBytes = maxBufferBytes;
    }

    public void setMaxBufferBytes(LongSupplier maxBufferBytes) {
        this.maxBufferBytes = maxBufferBytes;
    }

    public Collection<PendingWrite> getUnFlushList() {
        return Collections.unmodifiableCollection(queue);
    }

    public long getMaxBufferBytes() {
        return maxBufferBytes.getAsLong();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    /**
     * Continues to fetch the chunks from the input.
     */
    public void resumeTransfer() {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            return;
        }
        if (ctx.executor().inEventLoop()) {
            resumeTransfer0(ctx);
        } else {
            // let the transfer resume on the next event loop round
            ctx.executor().execute(() -> resumeTransfer0(ctx));
        }
    }

    private void resumeTransfer0(ChannelHandlerContext ctx) {
        try {
            doFlush(ctx);
        } catch (Exception e) {
            LOGGER.warn("Unexpected exception while sending chunks.", e);
        }
    }

    private PendingWrite removeFirst(){
        PendingWrite remove = queue.poll();
        if(remove != null) {
            unFlushBytes.addAndGet(-remove.bytes);
        }
        return remove;
    }

    private void add(PendingWrite pendingWrite){
        queue.add(pendingWrite);
        unFlushBytes.addAndGet(pendingWrite.bytes);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        add(PendingWrite.newInstance(msg, promise));

        if(unFlushBytes.get() >= maxBufferBytes.getAsLong()){
            doFlush(ctx);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        doFlush(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        doFlush(ctx);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            // channel is writable again try to continue flushing
            doFlush(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    public int unWriteSize() {
        return queue.size();
    }

    public void discard(Throwable cause) {
        List<PendingWrite> responseList = new ArrayList<>();
        PendingWrite currentWrite;
        for (; ; ) {
            currentWrite = removeFirst();
            if (currentWrite == null) {
                break;
            }
            Object message = currentWrite.msg;
            if (message instanceof ChunkedInput) {
                ChunkedInput<?> in = (ChunkedInput<?>) message;
                boolean endOfInput;
                long inputLength;
                try {
                    endOfInput = in.isEndOfInput();
                    inputLength = in.length();
                    closeInput(in);
                } catch (Exception e) {
                    closeInput(in);
                    currentWrite.fail(e);
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn(ChunkedInput.class.getSimpleName() + " failed", e);
                    }
                    continue;
                }

                if (!endOfInput) {
                    if (cause == null) {
                        cause = new ClosedChannelException();
                    }
                    currentWrite.fail(cause);
                } else {
                    currentWrite.success(inputLength);
                }
            } else if (message instanceof HttpResponse) {
                responseList.add(currentWrite);
            } else {
                if (cause == null) {
                    cause = new ClosedChannelException();
                }
                currentWrite.fail(cause);
            }
        }
        for (PendingWrite httpResponse : responseList) {
            add(httpResponse);
        }
    }

    private void doFlush(final ChannelHandlerContext ctx) {
        if (queue.isEmpty()) {
            ctx.flush();
            return;
        }
        if (flushIng.compareAndSet(false, true)) {
            try {
                doFlush0(ctx);
            } finally {
                flushIng.set(false);
            }
        }
    }

    private void doFlush0(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        if (!channel.isActive()) {
            discard(null);
            return;
        }

        boolean requiresFlush = true;
        ByteBufAllocator allocator = ctx.alloc();
        while (channel.isWritable()
//                || unFlushBytes.longValue() >= 0
        ) {
            final PendingWrite currentWrite = queue.peek();

            if (currentWrite == null) {
                break;
            }

            if (currentWrite.promise.isDone()) {
                // This might happen e.g. in the case when a write operation
                // failed, but there're still unconsumed chunks left.
                // Most chunked input sources would stop generating chunks
                // and report end of input, but this doesn't work with any
                // source wrapped in HttpChunkedInput.
                // Note, that we're not trying to release the message/chunks
                // as this had to be done already by someone who resolved the
                // promise (using ChunkedInput.close method).
                // See https://github.com/netty/netty/issues/8700.
                removeFirst();
                continue;
            }

            final Object pendingMessage = currentWrite.msg;

            if (pendingMessage instanceof ChunkedInput) {
                final ChunkedInput<?> chunks = (ChunkedInput<?>) pendingMessage;
                boolean endOfInput;
                boolean suspend;
                Object message = null;
                try {
                    message = chunks.readChunk(allocator);
                    endOfInput = chunks.isEndOfInput();

                    if (message == null) {
                        // No need to suspend when reached at the end.
                        suspend = !endOfInput;
                    } else {
                        suspend = false;
                    }
                } catch (final Throwable t) {
                    removeFirst();

                    if (message != null) {
                        ReferenceCountUtil.release(message);
                    }

                    closeInput(chunks);
                    currentWrite.fail(t);
                    break;
                }

                if (suspend) {
                    // ChunkedInput.nextChunk() returned null and it has
                    // not reached at the end of input. Let's wait until
                    // more chunks arrive. Nothing to write or notify.
                    break;
                }

                if (message == null) {
                    // If message is null write an empty ByteBuf.
                    // See https://github.com/netty/netty/issues/1671
                    message = Unpooled.EMPTY_BUFFER;
                }

                // Flush each chunk to conserve memory
                ChannelFuture f = ctx.writeAndFlush(message);
                if (endOfInput) {
                    removeFirst();

                    if (f.isDone()) {
                        handleEndOfInputFuture(f, currentWrite);
                    } else {
                        // Register a listener which will close the input once the write is complete.
                        // This is needed because the Chunk may have some resource bound that can not
                        // be closed before its not written.
                        //
                        // See https://github.com/netty/netty/issues/303
                        f.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                handleEndOfInputFuture(future, currentWrite);
                            }
                        });
                    }
                } else {
                    final boolean resume = !channel.isWritable();
                    if (f.isDone()) {
                        handleFuture(f, currentWrite, resume);
                    } else {
                        f.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                handleFuture(future, currentWrite, resume);
                            }
                        });
                    }
                }
                requiresFlush = false;
            } else {
                if (pendingMessage instanceof Flushable) {
                    try {
                        ((Flushable) pendingMessage).flush();
                    } catch (IOException e) {
                        LOGGER.warn(pendingMessage.getClass().getSimpleName() + " flush error", e);
                    }
                }
                removeFirst();
                ctx.write(pendingMessage, currentWrite.promise);
                currentWrite.recycle();
                requiresFlush = true;
            }

            if (!channel.isActive()) {
                discard(new ClosedChannelException());
                break;
            }
        }

        if (requiresFlush) {
            ctx.flush();
        }
    }

    private static void handleEndOfInputFuture(ChannelFuture future, PendingWrite currentWrite) {
        ChunkedInput<?> input = (ChunkedInput<?>) currentWrite.msg;
        if (!future.isSuccess()) {
            closeInput(input);
            currentWrite.fail(future.cause());
        } else {
            // read state of the input in local variables before closing it
            long inputProgress = input.progress();
            long inputLength = input.length();
            closeInput(input);
            currentWrite.progress(inputProgress, inputLength);
            currentWrite.success(inputLength);
        }
    }

    private void handleFuture(ChannelFuture future, PendingWrite currentWrite, boolean resume) {
        ChunkedInput<?> input = (ChunkedInput<?>) currentWrite.msg;
        if (!future.isSuccess()) {
            closeInput(input);
            currentWrite.fail(future.cause());
        } else {
            currentWrite.progress(input.progress(), input.length());
            if (resume && future.channel().isWritable()) {
                resumeTransfer();
            }
        }
    }

    private static void closeInput(ChunkedInput<?> chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Failed to close a chunked input.", t);
            }
        }
    }

    private static final class PendingWrite implements Recyclable {
        private static final Recycler<PendingWrite> RECYCLER = new Recycler<>(PendingWrite::new);
        Object msg;
        ChannelPromise promise;
        long bytes;

        PendingWrite() {
        }

        public static PendingWrite newInstance(Object msg, ChannelPromise promise) {
            PendingWrite instance = RECYCLER.getInstance();
            instance.msg = msg;
            instance.promise = promise;
            if (msg instanceof ByteBuf) {
                instance.bytes = ((ByteBuf) msg).readableBytes();
            } else if (msg instanceof ByteBuffer) {
                instance.bytes = ((ByteBuffer) msg).remaining();
            } else if (msg instanceof ByteBufHolder) {
                instance.bytes = ((ByteBufHolder) msg).content().readableBytes();
            } else if (msg instanceof ChunkedInput) {
                instance.bytes = Math.max(((ChunkedInput) msg).length(), 0);
            } else if (msg instanceof FileRegion) {
                instance.bytes = ((FileRegion) msg).count() - ((FileRegion) msg).position();
            } else {
                instance.bytes = 0;
            }
            return instance;
        }

        @Override
        public String toString() {
            return "PendingWrite{" +
                    "msg=" + msg +
                    ", bytes=" + bytes +
                    '}';
        }

        void fail(Throwable cause) {
            ReferenceCountUtil.release(msg);
            promise.tryFailure(cause);
            recycle();
        }

        void success(long total) {
            if (promise.isDone()) {
                // No need to notify the progress or fulfill the promise because it's done already.
                return;
            }
            progress(total, total);
            promise.trySuccess();
            recycle();
        }

        void progress(long progress, long total) {
            if (promise instanceof ChannelProgressivePromise) {
                ((ChannelProgressivePromise) promise).tryProgress(progress, total);
            }
        }

        @Override
        public void recycle() {
            this.msg = null;
            this.promise = null;
            RECYCLER.recycleInstance(this);
        }
    }

    @Override
    public String toString() {
        return "ChunkedWriteHandler{" +
                "maxBufferBytes=" + maxBufferBytes +
                ", unFlushBytes=" + unFlushBytes +
                ", queueSize=" + queue.size() +
                ", ctx=" + ctx +
                '}';
    }
}
