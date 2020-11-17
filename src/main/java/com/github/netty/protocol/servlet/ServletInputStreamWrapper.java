package com.github.netty.protocol.servlet;

import com.github.netty.core.util.IOUtil;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.Wrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import javax.servlet.ReadListener;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The servlet input stream
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletInputStreamWrapper extends javax.servlet.ServletInputStream implements Wrapper<CompositeByteBuf>, Recyclable {
    private AtomicBoolean closed = new AtomicBoolean(false); //Whether the input stream has been closed to ensure thread safety
    private CompositeByteBuf source;
    private long contentLength;
    private ReadListener readListener;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public ServletInputStreamWrapper() {}

    public long getContentLength() {
        return contentLength;
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        checkClosed();
        return super.readLine(b, off, len); //Template method, which invokes the read() method of the current class implementation
    }

    public static void main(String[] args) {
        CompositeByteBuf c = ByteBufAllocator.DEFAULT.compositeBuffer();
        c.addComponent(Unpooled.wrappedBuffer("123".getBytes()));
        IOUtil.writerModeToReadMode(c);
        String byteBuf = c.readBytes(c.readableBytes()).toString(Charset.defaultCharset());

        c.addComponent(Unpooled.wrappedBuffer("456".getBytes()));
        IOUtil.writerModeToReadMode(c);
        String byteBuf1 = c.readBytes(c.readableBytes()).toString(Charset.defaultCharset());

        c.addComponent(Unpooled.wrappedBuffer("".getBytes()));
    }

    public void onMessage(Object message){
        ByteBuf byteBuf = null;
        if(message instanceof ByteBuf){
            byteBuf = (ByteBuf) message;
        }

        if(byteBuf == null) {
            return;
        }

        int readableBytes = source.readableBytes();
        source.addComponent(byteBuf);
        IOUtil.writerModeToReadMode(source);

        lock.lock();
        try{
            condition.signalAll();
        }finally {
            lock.unlock();
        }

        ReadListener readListener = this.readListener;
        if(readListener != null){
            if(readableBytes == 0) {
                try {
                    readListener.onDataAvailable();
                } catch (IOException e) {
                    readListener.onError(e);
                }
            }
            if(source.capacity() >= contentLength){
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
        return contentLength == -1 || source.capacity() >= contentLength;
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
        if (contentLength == -1) {
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

    private void awaitDataIfNeed() throws IOException {
        while (!isFinished() && source.readableBytes() == 0){
            lock.lock();
            try {
                condition.await();
            } catch (InterruptedException e) {
                throw new IOException("read data interrupted",e);
            }finally {
                lock.unlock();
            }
        }
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
        Objects.requireNonNull(source);

        this.closed.set(false);
        this.source = source;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
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
