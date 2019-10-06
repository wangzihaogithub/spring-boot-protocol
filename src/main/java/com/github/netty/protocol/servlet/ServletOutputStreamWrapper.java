package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Wrapper;
import io.netty.channel.ChannelFutureListener;

import javax.servlet.*;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Servlets output streams (wrapper classes) that control access to the flow
 * @author wangzihao
 */
public class ServletOutputStreamWrapper extends javax.servlet.ServletOutputStream
        implements Wrapper<ServletOutputStream>,Recyclable{
    /**
     * The source data
     */
    private ServletOutputStream source;
    /**
     * Pause symbol
     */
    private boolean suspendFlag = false;

    private ChannelFutureListener closeListener;

    public ServletOutputStreamWrapper(ChannelFutureListener closeListener) {
        this.closeListener = closeListener;
    }

    /**
     * Set (on/off) to pause the output operation
     * @param suspendFlag True = pause, false= resume
     */
    public void setSuspendFlag(boolean suspendFlag) {
        this.suspendFlag = suspendFlag;
    }

    /**
     * Whether to pause the operation output stream
     * @return boolean suspendFlag
     */
    public boolean isSuspendFlag() {
        return suspendFlag;
    }

    @Override
    public boolean isReady() {
        return source.isReady();
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        source.setWriteListener(listener);
    }

    @Override
    public void write(int b) throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.write(b);
    }

    @Override
    public void close() throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.close();
    }

    @Override
    public void flush() throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.flush();
    }

    public void resetBuffer(){
        if(isSuspendFlag()){
            return;
        }
        source.resetBuffer();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.write(b);
    }

    @Override
    public void wrap(ServletOutputStream source) {
        if(closeListener != null) {
            source.setCloseListener(closeListener);
        }
        this.source = source;
    }

    @Override
    public ServletOutputStream unwrap() {
        return source;
    }

    @Override
    public <T> void recycle(Consumer<T> consumer) {
        if(source != null){
            ServletOutputStream out = source;
            source = null;
            out.recycle(consumer);
        }
        if(suspendFlag){
            suspendFlag = false;
        }
    }
}
