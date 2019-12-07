package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.Wrapper;
import io.netty.buffer.ByteBuf;

import javax.servlet.ReadListener;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The servlet input stream
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletInputStreamWrapper extends javax.servlet.ServletInputStream implements Wrapper<ByteBuf>, Recyclable {
    private AtomicBoolean closed = new AtomicBoolean(false); //Whether the input stream has been closed to ensure thread safety
    private ByteBuf source;
    private int contentLength;
    private ReadListener readListener;

    public ServletInputStreamWrapper() {}

    public ServletInputStreamWrapper(ByteBuf source) {
        wrap(source);
    }

    public int getContentLength() {
        return contentLength;
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        checkClosed();
        return super.readLine(b, off, len); //Template method, which invokes the read() method of the current class implementation
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
        return source.readableBytes() == 0;
    }

    /**
     * HttpContent has been read in at least once and not all of it has been read, or the HttpContent queue is not empty
     */
    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        this.readListener = readListener;
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
        if (isFinished()) {
            return -1;
        }

        //Read len bytes
        ByteBuf byteBuf = readContent(len);
        //Total number of readable bytes
        int readableBytes = byteBuf.readableBytes();
        //Copy to the bytes array
        byteBuf.readBytes(bytes, off, readableBytes);
        //Returns the number of bytes actually read
        return readableBytes - byteBuf.readableBytes();
    }

    /**
     * Try updating current, then read a byte, and return, where int is returned, but third-party frameworks treat it as one byte instead of four
     */
    @Override
    public int read() throws IOException {
        checkClosed();
        if (isFinished()) {
            return -1;
        }
        return source.readByte();
    }

    /**
     * Read length bytes from it
     */
    private ByteBuf readContent(int length) {
        if (length < source.readableBytes()) {
            return source.readSlice(length);
        } else {
            return source;
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
    public void wrap(ByteBuf source) {
        Objects.requireNonNull(source);

        this.closed.set(false);
        this.source = source;
        this.contentLength = source.capacity();
    }

    @Override
    public ByteBuf unwrap() {
        return source;
    }

    @Override
    public void recycle() {
        if(!isClosed()) {
            close();
        }
    }
}
