package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import io.netty.util.internal.PlatformDependent;

import javax.servlet.ReadListener;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The servlet input stream
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletInputStreamWrapper extends javax.servlet.ServletInputStream implements Wrapper<CompositeByteBuf>, Recyclable {
    private AtomicBoolean closed = new AtomicBoolean(false);
    private CompositeByteBuf source;
    private long contentLength;
    private AtomicLong receiveContentLength = new AtomicLong();
    private ReadListener readListener;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Supplier<InterfaceHttpPostRequestDecoder> requestDecoderSupplier;
    private volatile HttpPostRequestDecoder.ErrorDataDecoderException decoderException;
    private boolean needCloseClient;
    private long fileUploadTimeoutMs;
    private boolean receiveDataTimeout;

    public ServletInputStreamWrapper() {}

    public long getContentLength() {
        return contentLength;
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        checkClosed();
        return super.readLine(b, off, len);
    }

    public void onMessage(Object message){
        HttpContent httpContent;
        ByteBuf byteBuf;
        if(message instanceof HttpContent){
            httpContent = (HttpContent) message;
            byteBuf = httpContent.content();
        }else {
            RecyclableUtil.release(message);
            throw new IllegalStateException("unkown messsage. "+ message.getClass()+". "+message);
        }


        if(contentLength == -1){
            LoggerFactoryX.getLogger(ServletInputStreamWrapper.class).warn(
                    "not exist contentLength, but receive message。 {}/bytes, message = '{}'",
                    byteBuf.readableBytes(),byteBuf.toString(byteBuf.readerIndex(),255,Charset.defaultCharset()));
            RecyclableUtil.release(httpContent);
            return;
        }

        receiveContentLength.addAndGet(byteBuf.readableBytes());

        byteBuf.markReaderIndex();
        int readableBytes = source.readableBytes();
        source.addComponent(byteBuf);
        IOUtil.writerModeToReadMode(source);

        ReadListener readListener = this.readListener;
        InterfaceHttpPostRequestDecoder requestDecoder = this.requestDecoderSupplier.get();
        if(requestDecoder != null){
            try {
                requestDecoder.offer(httpContent);
            }catch (HttpPostRequestDecoder.ErrorDataDecoderException e){
                this.decoderException = e;
                if(readListener != null) {
                    try {
                        readListener.onError(e);
                    }catch (Throwable t){
                        LoggerFactoryX.getLogger(ServletInputStreamWrapper.class).error(
                                "readListener onError exception. source = {}, again trigger",e.toString(),t.toString(),t);
                    }
                }
            }finally {
                byteBuf.resetReaderIndex();
            }
        }

        if(isFinished()) {
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        if(readListener != null){
            if(readableBytes == 0) {
                try {
                    readListener.onDataAvailable();
                } catch (IOException e) {
                    readListener.onError(e);
                }
            }
            if(isFinished()){
                try {
                    readListener.onAllDataRead();
                } catch (IOException e) {
                    readListener.onError(e);
                }
            }
        }
    }

    /**
     * There is no new HttpContent input for this request, and all of the current content has been read
     * @return True = false after reading
     */
    @Override
    public boolean isFinished() {
        if(closed.get()){
            return true;
        }
        return contentLength == -1
                || receiveContentLength.get() >= contentLength
                || decoderException != null
                || receiveDataTimeout;
    }

    /**
     * HttpContent has been read in at least once and not all of it has been read, or the HttpContent queue is not empty
     */
    @Override
    public boolean isReady() {
        return contentLength == -1 || source.readableBytes() != 0;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        this.readListener = readListener;
        if (isFinished()) {
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

    /**
     * Skip n bytes
     */
    @Override
    public long skip(long n) throws IOException {
        checkClosed();
        long skipLen = Math.min(source.readableBytes(), n); //实际可以跳过的字节数
        source.skipBytes((int) skipLen);
        return skipLen;
    }

    /**
     * @return Number of readable bytes
     */
    @Override
    public int available() throws IOException {
        checkClosed();
        return null == source ? 0 : source.readableBytes();
    }

    @Override
    public void close() {
        ByteBuf source = this.source;
        if(source != null){
            RecyclableUtil.release(source);
            this.source = null;
        }
        this.readListener = null;
        this.decoderException = null;
    }

    /**
     * Try to update current, then read len bytes and copy to b (start with off subscript)
     * @return The number of bytes actually read
     */
    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        checkClosed();
        if (0 == len) {
            return 0;
        }

        awaitDataIfNeed();
        if(source.readableBytes() == 0){
            return -1;
        }

        int readableBytes = Math.min(len,source.readableBytes());
        source.readBytes(bytes, off, readableBytes);
        return readableBytes;
    }

    /**
     * Try updating current, then read a byte, and return, where int is returned, but third-party frameworks treat it as one byte instead of four
     */
    @Override
    public int read() throws IOException {
        checkClosed();

        awaitDataIfNeed();
        if (source.readableBytes() == 0) {
            return -1;
        }
        return source.readByte();
    }

    void awaitDataIfNeed() throws HttpPostRequestDecoder.ErrorDataDecoderException,IOException {
        while (!isFinished()){
            lock.lock();
            try {
                if(fileUploadTimeoutMs > 0) {
                    boolean isTimeout = condition.await(fileUploadTimeoutMs, TimeUnit.MILLISECONDS);
                    if (isTimeout) {
                        this.receiveDataTimeout = true;
                        this.needCloseClient = true;
                        throw new IOException("await client data stream timeout. timeout = " + fileUploadTimeoutMs + "/ms");
                    }
                }else {
                    condition.await();
                }
            } catch (InterruptedException e) {
                PlatformDependent.throwException(e);
            }finally {
                lock.unlock();
            }
        }
        HttpPostRequestDecoder.ErrorDataDecoderException decoderException = this.decoderException;
        if(decoderException != null){
            this.needCloseClient = true;
            throw decoderException;
        }
    }

    public boolean isNeedCloseClient() {
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
    public void wrap(CompositeByteBuf source) {
        this.closed.set(false);
        this.source = source;
        this.contentLength = -1;
        this.readListener = null;
        this.receiveContentLength.set(0);
        this.decoderException = null;
        this.needCloseClient = false;
        this.receiveDataTimeout = false;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public void setRequestDecoder(Supplier<InterfaceHttpPostRequestDecoder> requestDecoderSupplier) {
        this.requestDecoderSupplier = requestDecoderSupplier;
    }

    public void setFileUploadTimeoutMs(long fileUploadTimeoutMs) {
        this.fileUploadTimeoutMs = fileUploadTimeoutMs;
    }

    public long getFileUploadTimeoutMs() {
        return fileUploadTimeoutMs;
    }

    public Supplier<InterfaceHttpPostRequestDecoder> getRequestDecoder() {
        return requestDecoderSupplier;
    }

    @Override
    public CompositeByteBuf unwrap() {
        return source;
    }

    @Override
    public void recycle() {
        if(!isClosed()) {
            close();
        }
    }
}
