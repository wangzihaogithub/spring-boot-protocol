package com.github.netty.protocol.servlet;

import io.netty.channel.ChannelProgressivePromise;
import io.netty.handler.stream.*;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GenericProgressiveFutureListener;

import javax.servlet.ServletOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * use netty zero copy
 * if you need flush {@link ServletOutputStream#flush()}
 * @author wangzihaogithub
 * 2020-06-07 14:13:36
 */
public interface NettyOutputStream {
    /**
     * use netty batch write
     * @param input ChunkedInput
     * @see ChunkedFile
     * @see ChunkedNioStream
     * @see ChunkedNioFile
     * @see ChunkedStream
     * @return ChannelProgressivePromise
     * @throws IOException if close
     */
    ChannelProgressivePromise write(ChunkedInput input) throws IOException;

    /**
     * use netty zero copy
     * @param fileChannel FileChannel
     * @param count count
     * @param position position
     * @return ChannelProgressivePromise {@link ChannelProgressivePromise#addListener(GenericFutureListener)} }
     * @see GenericProgressiveFutureListener
     * @throws IOException if close
     */
    ChannelProgressivePromise write(FileChannel fileChannel, long position, long count) throws IOException;

    /**
     * use netty zero copy
     * @param file file
     * @param count count
     * @param position position
     * @return ChannelProgressivePromise {@link ChannelProgressivePromise#addListener(GenericFutureListener)} }
     * @see GenericProgressiveFutureListener
     * @throws IOException if close
     */
    ChannelProgressivePromise write(File file, long position, long count) throws IOException;

}
