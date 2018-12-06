package com.github.netty.register.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Wrapper;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

import javax.servlet.ReadListener;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * servlet 输入流
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 * @author acer01
 *  2018/7/15/015
 */
@sun.misc.Contended
public class ServletInputStream extends javax.servlet.ServletInputStream implements Wrapper<ByteBuf>, Recyclable {

    private AtomicBoolean closed = new AtomicBoolean(false); //输入流是否已经关闭，保证线程安全
    private ByteBuf source;
    private int contentLength;

    public ServletInputStream() {
    }

    public ServletInputStream(ByteBuf source) {
        wrap(source);
    }

    public int getContentLength() {
        return contentLength;
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        checkClosed();
        return super.readLine(b, off, len); //模板方法，会调用当前类实现的read()方法
    }

    /**
     * 本次请求没再有新的HttpContent输入，而且当前的内容全部被读完
     * @return true=读取完毕 反之false
     */
    @Override
    public boolean isFinished() {
        if(closed.get()){
            return true;
        }
        return source.readableBytes() == 0;
    }

    /**
     * 已读入至少一次HttpContent且未读取完所有内容，或者HttpContent队列非空
     */
    @Override
    public boolean isReady() {
        if(source == null){
            return true;
        }
        return source.readableBytes() > 0;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        // TODO: 10月16日/0016 监听写入事件
    }

    /**
     * 跳过n个字节
     */
    @Override
    public long skip(long n) throws IOException {
        checkClosed();
        long skipLen = Math.min(source.readableBytes(), n); //实际可以跳过的字节数
        source.skipBytes((int) skipLen);
        return skipLen;
    }

    /**
     * @return 可读字节数
     */
    @Override
    public int available() throws IOException {
        checkClosed();
        return null == source ? 0 : source.readableBytes();
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false,true)) {
            if(source != null && source.refCnt() > 0){
                ReferenceCountUtil.safeRelease(source);
                source = null;
            }
        }
    }

    /**
     * 尝试更新current，然后读取len个字节并复制到b中（off下标开始）
     * @return 实际读取的字节数
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

        //读取len个字节
        ByteBuf byteBuf = readContent(len);
        //总共可读的字节数
        int readableBytes = byteBuf.readableBytes();
        //复制到bytes数组
        byteBuf.readBytes(bytes, off, readableBytes);
        //返回实际读取的字节数
        return readableBytes - byteBuf.readableBytes();
    }

    /**
     * 尝试更新current，然后读取一个字节，并返回 ,这里虽然返回int, 但第三方框架都是按1个字节处理的,而不是4个字节
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
     * 从中读取length个字节
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
            try {
                close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
