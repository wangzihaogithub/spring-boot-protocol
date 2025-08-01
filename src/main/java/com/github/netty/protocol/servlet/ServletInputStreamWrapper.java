package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;

import javax.servlet.ReadListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The servlet input stream
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletInputStreamWrapper extends javax.servlet.ServletInputStream implements Wrapper<CompositeByteBuf>, Recyclable {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(ServletInputStreamWrapper.class);
    private static final FileAttribute[] EMPTY_FILE_ATTRIBUTE = {};
    private static final Set<? extends OpenOption> WRITE_OPTIONS = new HashSet<>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong receivedContentLength = new AtomicLong();
    private final AtomicLong readerIndex = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Supplier<InterfaceHttpPostRequestDecoder> requestDecoderSupplier;
    private final Supplier<ResourceManager> resourceManagerSupplier;
    private final AtomicBoolean onAllDataReadFlag = new AtomicBoolean();
    private final AtomicBoolean onDataAvailableFlag = new AtomicBoolean();
    private final AtomicBoolean receivedContentLengthFileSizeThresholdFlag = new AtomicBoolean();
    private final String identityName = getClass().getSimpleName() + System.identityHashCode(this) + "_";
    ServletHttpExchange httpExchange;
    private CompositeByteBuf source;
    long fileUploadTimeoutMs;
    int fileSizeThreshold;
    boolean needCloseClient;
    private volatile ReadListener readListener;
    private volatile DecoderException decoderException;
    volatile long contentLength;
    private volatile boolean receiveDataTimeout;
    private volatile boolean receivedLastHttpContent;
    private volatile FileInputStream uploadFileInputStream;
    private volatile SeekableByteChannel uploadFileOutputChannel;
    private volatile File uploadFile;
    private volatile boolean abort;
    private String uploadDir = ServletContext.DEFAULT_UPLOAD_DIR;
    private int uploadFileCount = 0;
    private Exception createFileException;
    private long mark = -1;
    private volatile ScheduledFuture<?> checkMessageChangeFuture;
    private final AtomicInteger version = new AtomicInteger();

    public ServletInputStreamWrapper(Supplier<InterfaceHttpPostRequestDecoder> requestDecoderSupplier, Supplier<ResourceManager> resourceManagerSupplier) {
        this.requestDecoderSupplier = requestDecoderSupplier;
        this.resourceManagerSupplier = resourceManagerSupplier;
    }

    public long getReaderIndex() {
        return readerIndex.get();
    }

    public long getReceivedContentLength() {
        return receivedContentLength.get();
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public long getContentLength() {
        return contentLength;
    }

    void onMessage(HttpContent httpContent) {
        try {
            if (closed.get()) {
                RecyclableUtil.release(httpContent);
            } else {
                onMessage0(httpContent);
            }
        } finally {
            if (httpContent instanceof LastHttpContent) {
                this.receivedLastHttpContent = true;
                conditionSignalAll();
            }
        }
    }

    private void onMessage0(HttpContent httpContent) {
        ByteBuf byteBuf = httpContent.content();
        int readableBytes = byteBuf.readableBytes();
        boolean release = true;
        try {
            InterfaceHttpPostRequestDecoder requestDecoder;
            try {
                requestDecoder = this.requestDecoderSupplier.get();
            } catch (DecoderException e) {
                requestDecoder = null;
                this.decoderException = e;
            }
            if (requestDecoder != null) {
                int markReaderIndex = byteBuf.readerIndex();
                try {
                    requestDecoder.offer(httpContent);
                } catch (Throwable e) {
                    if (e instanceof DecoderException) {
                        this.decoderException = (DecoderException) e;
                    } else {
                        this.decoderException = new DecoderException("ServletInputStreamWrapper#onMessage -> requestDecoder.offer(httpContent) error!", e);
                    }
                    if (readListener != null) {
                        try {
                            readListener.onError(e);
                        } catch (Throwable t) {
                            LOGGER.warn("readListener onError exception. source = {}, again trigger", e.toString(), t.toString(), t);
                        }
                    }
                } finally {
                    byteBuf.readerIndex(markReaderIndex);
                }
            }

            SeekableByteChannel outputChannel;
            if (requestDecoder != null && contentLength > fileSizeThreshold && (outputChannel = getUploadFileOutputChannel()) != null) {
                //In File temp
                try {
                    outputChannel.write(byteBuf.nioBuffer());
                } catch (IOException e) {
                    LOGGER.warn("upload file write temp file exception. file = {}, message = {}", uploadFile, e.toString(), e);
                }
            } else if (requestDecoder != null && (receivedContentLength.get() + readableBytes) > fileSizeThreshold && (outputChannel = getUploadFileOutputChannel()) != null) {
                //In File temp
                try {
                    int writeSize = 0;
                    boolean writeSource = false;
                    if (receivedContentLengthFileSizeThresholdFlag.compareAndSet(false, true)) {
                        writeSource = true;
                        if (source != null && source.isReadable()) {
                            for (ByteBuffer byteBuffer : source.nioBuffers()) {
                                int w = outputChannel.write(byteBuffer);
                                if (w > 0) {
                                    writeSize += w;
                                }
                            }
                            source.removeComponents(0, source.numComponents());
                        }
                    }
                    int w = outputChannel.write(byteBuf.nioBuffer());
                    if (w > 0) {
                        writeSize += w;
                    }
                    if (writeSource && readerIndex.get() > 0L) {
                        uploadFileInputStream.skip(Math.min(writeSize, readerIndex.get()));
                    }
                } catch (IOException e) {
                    LOGGER.warn("upload file write temp file exception. file = {}, message = {}", uploadFile, e.toString(), e);
                }
            } else if (source != null) {
                //In memory
                source.addComponent(byteBuf);
                source.writerIndex(source.capacity());
                release = false;
            }
            receivedContentLength.addAndGet(readableBytes);
        } finally {
            if (release) {
                RecyclableUtil.release(byteBuf);
            }
        }

        conditionSignalAll();

        boolean received = isReceived();
        SeekableByteChannel uploadFileOutputChannel = this.uploadFileOutputChannel;
        if (received && uploadFileOutputChannel != null) {
            try {
                uploadFileOutputChannel.close();
            } catch (FileNotFoundException | SecurityException e) {
                LOGGER.warn("upload file open temp file excetion. file = {}, message = {}", uploadFile, e.toString(), e);
            } catch (IOException ignored) {
            }
        }

        ReadListener readListener = this.readListener;
        if (readListener != null) {
            if (onDataAvailableFlag.compareAndSet(false, true)) {
                try {
                    readListener.onDataAvailable();
                    onDataAvailableFlag.set(false);
                } catch (Throwable e) {
                    onDataAvailableFlag.set(false);
                    readListener.onError(e);
                }
            }
        }
    }

    private void conditionSignalAll() {
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void addReaderIndex(long readableBytes) {
        if (readableBytes > 0) {
            if (readerIndex.addAndGet(readableBytes) >= contentLength && onAllDataReadFlag.compareAndSet(false, true)) {
                final ReadListener readListener = this.readListener;
                if (readListener != null) {
                    Executor executor = httpExchange.servletContext.getExecutor();
                    executor.execute(() -> {
                        try {
                            readListener.onAllDataRead();
                        } catch (IOException e) {
                            readListener.onError(e);
                        }
                    });
                }
            }
        }
    }

    private SeekableByteChannel getUploadFileOutputChannel() {
        if (createFileException != null) {
            return null;
        }
        if (uploadFileOutputChannel == null) {
            synchronized (this) {
                if (uploadFileOutputChannel == null) {
                    ResourceManager resourceManager = resourceManagerSupplier.get();
                    Path path = resourceManager.mkdirs(uploadDir);
                    String fileName = identityName + (++uploadFileCount) + ".tmp";
                    Path uploadFile = path.resolve(fileName);
                    try {
                        this.uploadFileOutputChannel = uploadFile.getFileSystem().provider().newByteChannel(uploadFile, WRITE_OPTIONS, EMPTY_FILE_ATTRIBUTE);
                        this.uploadFile = uploadFile.toFile();
                        this.uploadFileInputStream = new FileInputStream(this.uploadFile);
                    } catch (Exception e) {
                        this.createFileException = e;
                        LOGGER.warn("upload file create temp file Exception. file = {}, message = {}", uploadFile, e.toString(), e);
                    }
                }
            }
        }
        return uploadFileOutputChannel;
    }

    @Override
    public boolean markSupported() {
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            return uploadFileInputStream.markSupported();
        } else {
            return true;
        }
    }

    @Override
    public void mark(int readlimit) {
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            uploadFileInputStream.mark(readlimit);
            this.mark = readerIndex.get();
        } else if (source != null) {
            this.mark = source.readerIndex();
            source.markReaderIndex();
        }
    }

    @Override
    public void reset() throws IOException {
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            uploadFileInputStream.reset();
            this.readerIndex.set(mark);
        } else if (source != null) {
            source.resetReaderIndex();
            this.readerIndex.set(mark);
        }
    }

    public boolean isReceived() {
        long contentLength = this.contentLength;
        if (contentLength == -1) {
            return receivedLastHttpContent;
        } else {
            return closed.get() || receivedContentLength.get() >= contentLength || decoderException != null || receiveDataTimeout;
        }
    }

    public boolean isReadable(int readLength) {
        // isReadable里所有依赖的volatile字段发生变化后，都需要唤醒condition.await重新检查
        long contentLength = this.contentLength;
        if (contentLength == -1) {
            return readLength != -1 || receivedLastHttpContent;
        } else {
            long readableContentLength = readLength == -1 ? contentLength : Math.min(readerIndex.get() + readLength, contentLength);
            return receivedContentLength.get() >= readableContentLength
                    || decoderException != null
                    || receiveDataTimeout;
        }
    }

    /**
     * Returns true when all the data from the stream has been read else
     * it returns false.
     *
     * @return when all the data from the strea
     */
    @Override
    public boolean isFinished() {
        boolean isFinished;
        if (closed.get()) {
            isFinished = true;
        } else if (isReceived()) {
            FileInputStream uploadFileInputStream = this.uploadFileInputStream;
            if (uploadFileInputStream != null) {
                try {
                    isFinished = uploadFileInputStream.available() <= 0;
                } catch (IOException e) {
                    isFinished = true;
                }
            } else {
                isFinished = null == source || !source.isReadable();
            }
        } else {
            isFinished = false;
        }
        return isFinished;
    }

    /**
     * HttpContent has been read in at least once and not all of it has been read, or the HttpContent queue is not empty
     */
    @Override
    public boolean isReady() {
        return closed.get() || isReadable(1);
    }

    /**
     * Skip n bytes
     */
    @Override
    public long skip(long n) throws IOException {
        checkClosed();
        long skipLen;
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            skipLen = uploadFileInputStream.skip(n);
        } else {
            CompositeByteBuf source = this.source;
            if (source == null) {
                return 0;
            }
            skipLen = Math.min(source.readableBytes(), n);
            source.skipBytes((int) skipLen);
        }
        addReaderIndex(skipLen);
        return skipLen;
    }

    /**
     * @return Number of readable bytes
     */
    @Override
    public int available() throws IOException {
        checkClosed();
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            return uploadFileInputStream.available();
        } else {
            return null == source ? 0 : source.readableBytes();
        }
    }

    /**
     * 触发客户端断开有两种情况
     * 情况1. 客户端提前断开，确实没将body传完
     * 情况2. 客户端没提前断开，将body传完了，只是服务端IO忙不过来了。
     * 因为服务端IO忙不过来时，服务端无法区分出情况1还是情况2，所以靠定期检查，如果规定时间内还没收到新body，则真正触发abort。
     */
    void abort() {
        if (isReceived()) {
            return;
        }
        this.checkMessageChangeFuture = scheduleCheckAbortAfterMessageTimeout();
    }

    /**
     * 客户端断开后，如果超过abortAfterMessageTimeoutMs后，还没有收到完整的body，则抛出abort异常
     *
     * @return 任务Future
     */
    private ScheduledFuture<?> scheduleCheckAbortAfterMessageTimeout() {
        // 记录下执行前收到的数据大小
        long snapshotLength = this.receivedContentLength.get();
        int snapshotVersion = this.version.get();

        // 使用GlobalEventExecutor去schedule。
        // 这时不能使用EventLoop线程去schedule，因为造成超时的原因是EventLoop处理IO事件忙不过来了，不能再给它加任务了。
        return GlobalEventExecutor.INSTANCE.schedule(() -> {
                    if (snapshotVersion != this.version.get() || this.closed.get() || isReceived()) {
                        return;
                    }
                    // timeout后仍然没有收到新的长度
                    if (snapshotLength == this.receivedContentLength.get()) {
                        // 标记终止, 抛给前端
                        this.abort = true;
                        this.checkMessageChangeFuture = null;
                        close();
                        // 释放等待读body的线程
                        conditionSignalAll();
                    } else {
                        // 可以收到新的body bytes，只是比较慢，应该是在压测中。
                        // 继续开始下次超时检测
                        this.checkMessageChangeFuture = scheduleCheckAbortAfterMessageTimeout();
                    }
                },
                httpExchange.servletContext.abortAfterMessageTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            conditionSignalAll();
            ByteBuf source = this.source;
            if (source != null) {
                RecyclableUtil.release(source);
                this.source = null;
            }
            this.readListener = null;
            this.decoderException = null;
            this.createFileException = null;
            this.checkMessageChangeFuture = null;
            ScheduledFuture<?> checkMessageChangeFuture = this.checkMessageChangeFuture;
            this.checkMessageChangeFuture = null;
            if (checkMessageChangeFuture != null) {
                checkMessageChangeFuture.cancel(false);
            }

            FileInputStream uploadFileInputStream = this.uploadFileInputStream;
            this.uploadFileInputStream = null;

            Channel uploadFileOutputChannel = this.uploadFileOutputChannel;
            this.uploadFileOutputChannel = null;

            File uploadFile = this.uploadFile;
            this.uploadFile = null;

            if (uploadFile != null || uploadFileInputStream != null || uploadFileOutputChannel != null) {
                ServletContext.asyncClose(() -> {
                    if (uploadFileInputStream != null) {
                        try {
                            uploadFileInputStream.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (uploadFileOutputChannel != null) {
                        try {
                            uploadFileOutputChannel.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (uploadFile != null) {
                        try {
                            uploadFile.delete();
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        checkClosed();
        return super.readLine(b, off, len);
    }

    /**
     * Try to update current, then read len bytes and copy to b (start with off subscript)
     *
     * @return The number of bytes actually read
     */
    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        checkClosed();
        if (0 == len) {
            return 0;
        }

        awaitDataIfNeed(1);

        int readableBytes;
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            readableBytes = uploadFileInputStream.read(bytes, off, len);
            addReaderIndex(readableBytes);
        } else if (source != null && source.isReadable()) {
            readableBytes = Math.min(source.readableBytes(), len);
            source.readBytes(bytes, off, readableBytes);
            addReaderIndex(readableBytes);
        } else {
            readableBytes = -1;
        }
        return readableBytes;
    }

    /**
     * Try updating current, then read a byte, and return, where int is returned, but third-party frameworks treat it as one byte instead of four
     */
    @Override
    public int read() throws IOException {
        checkClosed();

        awaitDataIfNeed(1);
        FileInputStream uploadFileInputStream = this.uploadFileInputStream;
        if (uploadFileInputStream != null) {
            int readableBytes = uploadFileInputStream.read();
            addReaderIndex(readableBytes);
            return readableBytes;
        } else if (this.source != null && this.source.isReadable()) {
            int readableBytes = source.readByte();
            addReaderIndex(readableBytes);
            return readableBytes;
        } else {
            return -1;
        }
    }

    void awaitDataIfNeed(int read) throws DecoderException, IOException {
        while (!closed.get() && !isReadable(read) && !abort) {
            lock.lock();
            try {
                if (fileUploadTimeoutMs > 0) {
                    boolean success = condition.await(fileUploadTimeoutMs, TimeUnit.MILLISECONDS);
                    if (!success) {
                        this.receiveDataTimeout = true;
                        this.needCloseClient = true;
                        throw new IOException("await client data stream timeout. timeout = " + fileUploadTimeoutMs + "/ms");
                    }
                } else {
                    condition.await();
                }
            } catch (InterruptedException e) {
                PlatformDependent.throwException(e);
            } finally {
                lock.unlock();
            }
        }
        if (abort) {
            throw new ServletClientAbortException("Unexpected end of stream while reading request body");
        }
        DecoderException decoderException = this.decoderException;
        if (decoderException != null) {
            this.needCloseClient = true;
            throw decoderException;
        }
        checkClosed();
    }

    private void checkClosed() throws IOException {
        if (closed.get()) {
            throw new IOException("Stream closed");
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public ReadListener getReadListener() {
        return readListener;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        this.readListener = readListener;
        boolean received = isReceived();
        if (received) {
            try {
                readListener.onDataAvailable();
            } catch (IOException e) {
                readListener.onError(e);
            }
            try {
                readListener.onAllDataRead();
            } catch (IOException e) {
                readListener.onError(e);
            }
        }
        onAllDataReadFlag.set(received);
    }

    @Override
    public void wrap(CompositeByteBuf source) {
        this.version.incrementAndGet();
        this.closed.set(false);
        this.onAllDataReadFlag.set(false);
        this.onDataAvailableFlag.set(false);
        this.receivedContentLengthFileSizeThresholdFlag.set(false);
        this.source = source;
        this.readListener = null;
        this.readerIndex.set(0L);
        this.receivedContentLength.set(0);
        this.receivedLastHttpContent = false;
        this.decoderException = null;
        this.needCloseClient = false;
        this.receiveDataTimeout = false;
        this.abort = false;
    }

    public long getFileUploadTimeoutMs() {
        return fileUploadTimeoutMs;
    }

    public int getUploadFileCount() {
        return uploadFileCount;
    }

    @Override
    public CompositeByteBuf unwrap() {
        return source;
    }

    @Override
    public void recycle() {
        if (!isClosed()) {
            close();
        }
    }
}
