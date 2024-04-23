package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import io.netty.util.internal.PlatformDependent;

import javax.servlet.ReadListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
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
    private static final Set<? extends OpenOption> WRITE_OPTIONS = new HashSet<>(Arrays.asList(
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong receiveContentLength = new AtomicLong();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Supplier<InterfaceHttpPostRequestDecoder> requestDecoderSupplier;
    private final Supplier<ResourceManager> resourceManagerSupplier;
    private final AtomicBoolean readListenerFlag = new AtomicBoolean(true);

    private ReadListener readListener;
    private CompositeByteBuf source;
    private long contentLength;
    private long fileUploadTimeoutMs;
    private int fileSizeThreshold;
    private boolean needCloseClient;

    private /*volatile*/ DecoderException decoderException;
    private volatile boolean receiveDataTimeout;
    private /*volatile*/ FileInputStream uploadFileInputStream;
    private volatile SeekableByteChannel uploadFileOutputChannel;
    private /*volatile*/ File uploadFile;
    private String uploadDir = "/upload/";
    private int uploadFileCount = 0;
    private Exception createFileException;

    public ServletInputStreamWrapper(Supplier<InterfaceHttpPostRequestDecoder> requestDecoderSupplier, Supplier<ResourceManager> resourceManagerSupplier) {
        this.requestDecoderSupplier = requestDecoderSupplier;
        this.resourceManagerSupplier = resourceManagerSupplier;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public void setFileSizeThreshold(int fileSizeThreshold) {
        this.fileSizeThreshold = fileSizeThreshold;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public void onMessage(HttpContent httpContent) {
        ByteBuf byteBuf = httpContent.content();
        int readableBytes = byteBuf.readableBytes();
        boolean release = true;
        try {
            if (contentLength == -1 && readableBytes > 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("not exist contentLength, but receive message。 {}/bytes, message = '{}'",
                        readableBytes, byteBuf.toString(byteBuf.readerIndex(), Math.min(readableBytes, 2048), Charset.forName("UTF-8")));
                return;
            }


            ReadListener readListener = this.readListener;
            InterfaceHttpPostRequestDecoder requestDecoder;
            try {
                requestDecoder = this.requestDecoderSupplier.get();
            } catch (DecoderException e) {
                requestDecoder = null;
                this.decoderException = e;
            }
            if (requestDecoder != null) {
                byteBuf.markReaderIndex();
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
                    byteBuf.resetReaderIndex();
                }
            }

            SeekableByteChannel outputChannel;
            if (requestDecoder != null
                    && contentLength > fileSizeThreshold
                    && (outputChannel = getUploadFileOutputChannel()) != null) {
                //In File temp
                try {
                    outputChannel.write(byteBuf.nioBuffer());
                } catch (IOException e) {
                    LOGGER.warn("upload file write temp file exception. file = {}, message = {}", uploadFile, e.toString(), e);
                }
            } else if (source != null) {
                //In memory
                source.addComponent(byteBuf);
                source.writerIndex(source.capacity());
                release = false;
            }
            receiveContentLength.addAndGet(readableBytes);
        } finally {
            if (release) {
                RecyclableUtil.release(byteBuf);
            }
        }

        if (isReceived()) {
            if (uploadFile != null) {
                try {
                    uploadFileOutputChannel.close();
                    uploadFileOutputChannel = null;
                } catch (FileNotFoundException | SecurityException e) {
                    LOGGER.warn("upload file open temp file excetion. file = {}, message = {}", uploadFile, e.toString(), e);
                } catch (IOException ignored) {
                }
            }
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        if (readListener != null) {
            if (readListenerFlag.compareAndSet(true, false)) {
                try {
                    readListener.onDataAvailable();
                } catch (IOException e) {
                    readListener.onError(e);
                }
            }
            if (isReceived()) {
                try {
                    readListener.onAllDataRead();
                } catch (IOException e) {
                    readListener.onError(e);
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
                    String servletFile = resourceManagerSupplier.get().getRealPath(
                            uploadDir + hashCode() + "_" + (++uploadFileCount));
                    Path path = Paths.get(servletFile);
                    File uploadFile = path.toFile();
                    try {
                        this.uploadFileOutputChannel = createFileChannel(uploadFile, path);
                        this.uploadFile = uploadFile;
                        this.uploadFileInputStream = new FileInputStream(uploadFile);
                    } catch (Exception e) {
                        this.createFileException = e;
                        LOGGER.warn("upload file create temp file Exception. file = {}, message = {}", uploadFile, e.toString(), e);
                    }
                }
            }
        }
        return uploadFileOutputChannel;
    }

    private SeekableByteChannel createFileChannel(File file, Path path) throws IOException {
        boolean fileExist = file.exists();
        if (!fileExist) {
            File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentFile.mkdirs();
            }
            file.createNewFile();
        }
        return path.getFileSystem().provider().newByteChannel(path, WRITE_OPTIONS, EMPTY_FILE_ATTRIBUTE);
    }

    @Override
    public boolean markSupported() {
        if (uploadFileInputStream != null) {
            return uploadFileInputStream.markSupported();
        } else {
            return true;
        }
    }

    @Override
    public void mark(int readlimit) {
        if (uploadFileInputStream != null) {
            uploadFileInputStream.mark(readlimit);
        } else if (source != null) {
            source.markReaderIndex();
        }
    }

    @Override
    public void reset() throws IOException {
        if (uploadFileInputStream != null) {
            uploadFileInputStream.reset();
        } else if (source != null) {
            source.resetReaderIndex();
        }
    }

    public boolean isReceived() {
        return closed.get()
                || contentLength == -1
                || receiveContentLength.get() >= contentLength
                || decoderException != null
                || receiveDataTimeout;
    }

    /**
     * Returns true when all the data from the stream has been read else
     * it returns false.
     *
     * @return when all the data from the strea
     */
    @Override
    public boolean isFinished() {
        if (closed.get()) {
            return true;
        }
        if (uploadFileInputStream != null) {
            try {
                return uploadFileInputStream.available() == 0;
            } catch (IOException e) {
                return true;
            }
        } else if (null == source) {
            return true;
        } else {
            return !source.isReadable();
        }
    }

    /**
     * HttpContent has been read in at least once and not all of it has been read, or the HttpContent queue is not empty
     */
    @Override
    public boolean isReady() {
        return contentLength == -1 || (source == null || source.isReadable()) || uploadFileInputStream != null;
    }

    /**
     * Skip n bytes
     */
    @Override
    public long skip(long n) throws IOException {
        checkClosed();
        if (uploadFileInputStream != null) {
            return uploadFileInputStream.skip(n);
        } else {
            CompositeByteBuf source = this.source;
            if (source == null) {
                return 0;
            }
            long skipLen = Math.min(source.readableBytes(), n);
            source.skipBytes((int) skipLen);
            return skipLen;
        }
    }

    /**
     * @return Number of readable bytes
     */
    @Override
    public int available() throws IOException {
        checkClosed();
        if (uploadFileInputStream != null) {
            return uploadFileInputStream.available();
        } else {
            return null == source ? 0 : source.readableBytes();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            ByteBuf source = this.source;
            if (source != null) {
                RecyclableUtil.release(source);
                this.source = null;
            }
            this.readListener = null;
            this.decoderException = null;
            this.createFileException = null;

            FileInputStream uploadFileInputStream = this.uploadFileInputStream;
            if (uploadFileInputStream != null) {
                try {
                    uploadFileInputStream.close();
                } catch (Exception ignored) {
                }
                this.uploadFileInputStream = null;
            }

            File uploadFile = this.uploadFile;
            if (uploadFile != null) {
                try {
                    if (uploadFile.exists()) {
                        uploadFile.delete();
                    }
                } catch (Exception ignored) {
                }
                this.uploadFile = null;
            }

            Channel uploadFileOutputChannel = this.uploadFileOutputChannel;
            if (uploadFileOutputChannel != null) {
                try {
                    uploadFileOutputChannel.close();
                } catch (Exception ignored) {
                }
                this.uploadFileOutputChannel = null;
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

        awaitDataIfNeed();

        if (uploadFileInputStream != null) {
            return uploadFileInputStream.read(bytes, off, len);
        } else if (source != null) {
            if (!source.isReadable()) {
                return -1;
            }
            int readableBytes = Math.min(source.readableBytes(), len);
            source.readBytes(bytes, off, readableBytes);
            return readableBytes;
        } else {
            return -1;
        }
    }

    /**
     * Try updating current, then read a byte, and return, where int is returned, but third-party frameworks treat it as one byte instead of four
     */
    @Override
    public int read() throws IOException {
        checkClosed();

        awaitDataIfNeed();
        if (uploadFileInputStream != null) {
            return uploadFileInputStream.read();
        } else {
            CompositeByteBuf source = this.source;
            if (source == null) {
                return -1;
            }
            if (!source.isReadable()) {
                return -1;
            }
            return source.readByte();
        }
    }

    void awaitDataIfNeed() throws DecoderException, IOException {
        while (!isReceived()) {
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
        DecoderException decoderException = this.decoderException;
        if (decoderException != null) {
            this.needCloseClient = true;
            throw decoderException;
        }
    }

    boolean isNeedCloseClient() {
        return needCloseClient;
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
        if (isReady()) {
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
    }

    @Override
    public void wrap(CompositeByteBuf source) {
        this.closed.set(false);
        this.readListenerFlag.set(true);
        this.source = source;
        this.contentLength = -1;
        this.readListener = null;
        this.receiveContentLength.set(0);
        this.decoderException = null;
        this.needCloseClient = false;
        this.receiveDataTimeout = false;
    }

    public long getFileUploadTimeoutMs() {
        return fileUploadTimeoutMs;
    }

    public void setFileUploadTimeoutMs(long fileUploadTimeoutMs) {
        this.fileUploadTimeoutMs = fileUploadTimeoutMs;
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
