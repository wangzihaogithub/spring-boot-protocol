package com.github.netty.protocol.servlet;

import com.github.netty.core.util.HttpHeaderUtil;
import com.github.netty.core.util.IOUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servlet output stream (segmented)
 * @author wangzihao
 */
public class ServletOutputChunkedStream extends ServletOutputStream {
    private ByteChunkedInput chunkedInput = new ByteChunkedInput();

    protected ServletOutputChunkedStream() {}

    @Override
    public void flush() throws IOException {
        checkClosed();

        if(getBuffer() == null) {
            return;
        }
        flush0(null);
    }

    @Override
    public void close() throws IOException {
        if(super.isClosed.compareAndSet(false,true)){
            chunkedInput.setCloseInputFlag(true);
            flush0(getCloseListener());
        }
    }

    /**
     * Refresh buffer (listen for completion events)
     * @param listener Called after refreshing the buffer
     * @throws IOException
     */
    private void flush0(ChannelFutureListener listener) throws IOException {
        try {
            lock();
            ByteBuf content = getBuffer();
            if (content != null) {
                this.chunkedInput.setChunkByteBuf(content);
                setBuffer(null);
            }
        }finally {
            unlock();
        }

        if (super.isSendResponseHeader.compareAndSet(false,true)) {
            NettyHttpResponse nettyResponse = getServletHttpExchange().getResponse().getNettyResponse();
            LastHttpContent lastHttpContent = nettyResponse.enableTransferEncodingChunked();
            chunkedInput.setLastHttpContent(lastHttpContent);

            ChannelPipeline pipeline = getServletHttpExchange().getChannelHandlerContext().channel().pipeline();
            if(pipeline.context(ChunkedWriteHandler.class) == null) {
                ChannelHandlerContext httpContext = pipeline.context(HttpServerCodec.class);
                if(httpContext == null){
                    httpContext = pipeline.context(HttpRequestDecoder.class);
                }
                if(httpContext != null) {
                    ChunkedWriteHandler chunkedWriteHandler = new ChunkedWriteHandler();
                    try {
                        chunkedWriteHandler.handlerAdded(httpContext);
                    } catch (Exception e) {
                        //skip
                    }
                    pipeline.addAfter(
                            httpContext.name(), "ChunkedWrite",chunkedWriteHandler);
                }
            }

            super.sendResponse().addListener((ChannelFutureListener) future -> {
                Channel channel = future.channel();
                ChannelPromise promise;
                if(listener == null){
                    promise = channel.voidPromise();
                }else {
                    promise = channel.newProgressivePromise();
                    promise.addListener(listener);
                }
                channel.writeAndFlush(chunkedInput,promise);
            });

        }else {
            ChannelFuture channelFuture = getServletHttpExchange().getChannelHandlerContext().writeAndFlush(chunkedInput);
            if (listener != null) {
                try {
                    channelFuture.addListener(listener);
                } catch (Exception e) {
                    throw new IOException(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 字节块输入
     */
    static class ByteChunkedInput implements ChunkedInput<Object>{
        private boolean closeInputFlag = false;
        private boolean sendLastChunkFlag = false;
        private AtomicInteger readLength = new AtomicInteger();
        private ByteBuf chunkByteBuf;
        private LastHttpContent lastHttpContent;

        @Override
        public long length() {
            if(closeInputFlag){
                return readLength.get();
            }
            return -1;
        }

        @Override
        public long progress() {
            if(closeInputFlag){
                return readLength.get();
            }
            return -1;
        }

        @Override
        public boolean isEndOfInput() throws Exception {
            if (closeInputFlag) {
                // Only end of input after last HTTP chunk has been sent
                return sendLastChunkFlag;
            } else {
                return false;
            }
        }

        @Override
        public void close() throws Exception {
            chunkByteBuf = null;
        }

        @Deprecated
        @Override
        public Object readChunk(ChannelHandlerContext ctx) throws Exception {
            return readChunk(ctx.alloc());
        }

        @Override
        public Object readChunk(ByteBufAllocator allocator) throws Exception {
            if (sendLastChunkFlag) {
                return null;
            }

            ByteBuf byteBuf = chunkByteBuf;
            Object result;
            if (closeInputFlag) {
                // Send last chunk for this input
                sendLastChunkFlag = true;
                if(lastHttpContent != null){
                    //Trailer does not support the removal of the head of field
                    HttpHeaderUtil.removeHeaderUnSupportTrailer(lastHttpContent);
                    if(byteBuf != null){
                        lastHttpContent.content().writeBytes(byteBuf);
                    }
                    result = lastHttpContent;
                }else if(byteBuf != null){
                    result = new DefaultLastHttpContent(byteBuf);
                }else {
                    result = LastHttpContent.EMPTY_LAST_CONTENT;
                }
            } else {
                result = byteBuf;
            }
            if(byteBuf != null) {
                readLength.addAndGet(byteBuf.capacity());
            }
            chunkByteBuf = null;
            return result;
        }

        public void setLastHttpContent(LastHttpContent lastHttpContent) {
            this.lastHttpContent = lastHttpContent;
        }

        public void setCloseInputFlag(boolean isCloseInput) {
            this.closeInputFlag = isCloseInput;
        }

        public void setChunkByteBuf(ByteBuf chunkByteBuf) {
            //Switching the read mode
            IOUtil.writerModeToReadMode(chunkByteBuf);
            this.chunkByteBuf = chunkByteBuf;
        }
    }
}
