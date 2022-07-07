package com.github.netty.protocol.servlet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.handler.stream.*;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GenericProgressiveFutureListener;

import javax.servlet.ServletOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

/**
 * use netty zero copy
 * if you need flush {@link ServletOutputStream#flush()}
 * @author wangzihaogithub
 * 2020-06-07 14:13:36
 */
public interface NettyOutputStream extends Flushable, Closeable {

    /**
     * batch send packet. Immediately other side peer receive write finish data
     * Try not to use this method, it is possible for maximum performance.
     * Unless you need to need to get to the end part of the data received immediately
     * @throws IOException if close
     */
    @Override
    void flush() throws IOException;

    /**
     * close http.
     * if keepAlive. It does not turn off tcpConnection. Otherwise, turn off tcpConnection
     */
    @Override
    void close();

    /**
     * direct write to tcp outputStream.
     * @param httpBody jdk ByteBuffer httpBody
     * @see MappedByteBuffer
     * @see ByteBuffer
     * @return ChannelProgressivePromise
     * @throws IOException if close
     */
    ChannelProgressivePromise write(ByteBuffer httpBody) throws IOException;

    /**
     * direct write to tcp outputStream
     * @param httpBody netty ByteBuf httpBody
     * @see ChunkedFile
     * @see ChunkedNioStream
     * @see ChunkedNioFile
     * @see ChunkedStream
     * @return ChannelProgressivePromise
     * @throws IOException if close
     */
    ChannelProgressivePromise write(ByteBuf httpBody) throws IOException;

    /**
     * use netty batch write
     * @param httpBody ChunkedInput httpBody
     * @see ChunkedFile
     * @see ChunkedNioStream
     * @see ChunkedNioFile
     * @see ChunkedStream
     * @return ChannelProgressivePromise
     * @throws IOException if close
     */
    ChannelProgressivePromise write(ChunkedInput httpBody) throws IOException;

    /**
     * use netty zero copy
     * @param httpBody File httpBody
     * @param count count
     * @param position position
     * @return ChannelProgressivePromise {@link ChannelProgressivePromise#addListener(GenericFutureListener)} }
     * @see GenericProgressiveFutureListener
     * @throws IOException if close
     */
    ChannelProgressivePromise write(File httpBody, long position, long count) throws IOException;

    /**
     * use netty zero copy
     * @param httpBody File httpBody
     * @return ChannelProgressivePromise {@link ChannelProgressivePromise#addListener(GenericFutureListener)} }
     * @see GenericProgressiveFutureListener
     * @throws IOException if close
     */
    ChannelProgressivePromise write(File httpBody) throws IOException;

}
