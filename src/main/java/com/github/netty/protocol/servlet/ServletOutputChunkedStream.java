package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servlet output stream (chunk)
 * @author wangzihao
 */
public class ServletOutputChunkedStream extends ServletOutputStream {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(ServletOutputChunkedStream.class);
    private final ByteChunkedInput chunkedInput = new ByteChunkedInput();
    private final AtomicBoolean isSendResponseHeader = new AtomicBoolean(false);
    private final AtomicReference<ChannelPromise> currentPromiseReference = new AtomicReference<>(null);

    protected ServletOutputChunkedStream() {}

    @Override
    public void flush() throws IOException {
        checkClosed();

        if(getBuffer() == null) {
            return;
        }
        flushAsync(null);
    }

    @Override
    public void close() {
        if(super.isClosed.compareAndSet(false,true)){
            chunkedInput.setCloseInputFlag(true);
            CloseListener closeListener = super.getCloseListener();
            closeListener.addRecycleConsumer(o -> {
                isSendResponseHeader.set(false);
                currentPromiseReference.set(null);
                chunkedInput.recycle();
            });
            flushAsync(closeListener);
        }
    }

    /**
     * Async refresh buffer (listen for completion events)
     * If flush operation is being performed, it will be appended to execute this operation callback
     * The case of multiple IO will appear in the {@link #sendChunkedResponse}
     *
     * @param listener After the full execute of the callback method， The presence of multiple IO, so the last time callback
     * @return ChannelFuture. IO once after the callback method
     */
    private ChannelFuture flushAsync(ChannelFutureListener listener) {
//        getServletHttpExchange().touch(this);
        ChannelHandlerContext context = getServletHttpExchange().getChannelHandlerContext();
        ChannelPromise promise = context.newPromise();

        ChannelFuture endFuture = null;
        try {
            //try flush operation success
            if (currentPromiseReference.compareAndSet(null, promise)) {
                if (isSendResponseHeader.compareAndSet(false, true)) {
                    endFuture = sendChunkedResponse(listener, promise);
                } else if(listener == null){
                    endFuture = flushChunk(context.channel(), promise);
                }else {
                    endFuture = flushChunk(context.channel(), promise).addListener(listener);
                }
            } else {
                //try flush operation failure, appended to execute this operation callback
                ChannelPromise currentPromise = currentPromiseReference.get();
                if (currentPromise == null || currentPromise.isVoid()) {
                    endFuture = flushAsync(listener);
                } else {
                    endFuture = currentPromise.addListener(future -> flushAsync(listener));
                }
            }
        }finally {
            if(endFuture != null){
                endFuture.addListener(f -> currentPromiseReference.compareAndSet(promise, null));
            }else {
                currentPromiseReference.compareAndSet(promise,null);
            }
        }
        return endFuture;
    }

    /**
     * flush chunk data {@link #chunkedInput}
     * @param channel channel
     * @param promise promise
     * @return ChannelFuture The flush content callback
     */
    private ChannelFuture flushChunk(Channel channel, ChannelPromise promise) {
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

        ChannelFuture endFuture;
        if (chunkedInput.hasChunk()) {
            if(channel.isActive()){
                endFuture = channel.writeAndFlush(chunkedInput, promise);
            }else {
                Object discardPacket = chunkedInput.readChunk(channel.alloc());
                if(discardPacket != null) {
                    String discardPacketToString;
                    if(discardPacket instanceof ByteBuf){
                        discardPacketToString = ((ByteBuf) discardPacket).toString(Charset.defaultCharset());
                    }else {
                        discardPacketToString = discardPacket.toString();
                    }
                    LOGGER.warn("on sendChunkedResponse channel inactive. channel={}, discardPacket={}, packetType={}",
                            channel,
                            discardPacketToString,
                            discardPacket.getClass().getName());
                }
                endFuture = promise;
                promise.trySuccess();
            }
        } else{
            endFuture = promise;
            promise.trySuccess();
        }
        return endFuture;
    }

    /**
     * The response header sent, and then sent the response content
     * @param writerContentEndListener After the contents of the response is sent callback
     * @param promise compareAndSet used promise
     * @return ChannelPromise. After sending a response to callback
     */
    private ChannelFuture sendChunkedResponse(ChannelFutureListener writerContentEndListener,ChannelPromise promise){
        ServletHttpServletResponse servletResponse = getServletHttpExchange().getResponse();
        LastHttpContent lastHttpContent = servletResponse.getNettyResponse().enableTransferEncodingChunked();
        chunkedInput.setLastHttpContent(lastHttpContent);

        ChannelPipeline pipeline = promise.channel().pipeline();
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

        //see Http11Processor#prepareResponse
        return super.sendResponse().addListener((ChannelFutureListener) future -> {
            ChannelFuture endFuture;
            if(future.isSuccess()){
                Channel channel = future.channel();
                if(servletResponse.getContentLength() >= 0){
                    CompositeByteBufX content = getBuffer();
                    if(content != null){
                        IOUtil.writerModeToReadMode(content);
                        endFuture = channel.writeAndFlush(new DefaultLastHttpContent(content), promise);
                    }else {
                        endFuture = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT,promise);
                    }
                    chunkedInput.setSendLastChunkFlag(true);
                }else {
                    endFuture = flushChunk(channel,promise);
                }
            }else {
                endFuture = future.channel().close(promise);
            }

            if(writerContentEndListener != null){
                if(endFuture.isVoid()){
                    writerContentEndListener.operationComplete(future);
                }else {
                    endFuture.addListener(writerContentEndListener);
                }
            }
        });
    }

    /**
     * Chunked transfer input. Multiple IO writing.
     * Refer Http Chunked transfer
     */
    static class ByteChunkedInput implements ChunkedInput<Object>, Recyclable {
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
        public Object readChunk(ByteBufAllocator allocator) {
            if (sendLastChunkFlag) {
                return null;
            }

            ByteBuf byteBuf = chunkByteBuf;
            IOUtil.writerModeToReadMode(byteBuf);
            Object result;
            if (closeInputFlag) {
                // Send last chunk for this input
                sendLastChunkFlag = true;
                if(lastHttpContent != null){
                    //Trailer does not support the removal of the head of field
                    HttpHeaderUtil.removeHeaderUnSupportTrailer(lastHttpContent);
                    if(byteBuf != null){
                        DefaultLastHttpContent lastHttpContentCopy = new DefaultLastHttpContent(byteBuf, false);
                        lastHttpContentCopy.trailingHeaders().set(lastHttpContent.trailingHeaders());
                        result = lastHttpContentCopy;
                    }else {
                        result = lastHttpContent;
                    }
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

        public boolean hasChunk() {
            if(sendLastChunkFlag){
                return false;
            }
            if(closeInputFlag){
                return true;
            }
            return chunkByteBuf != null;
        }

        public void setSendLastChunkFlag(boolean sendLastChunkFlag) {
            this.sendLastChunkFlag = sendLastChunkFlag;
        }

        @Override
        public void recycle() {
            this.closeInputFlag = false;
            this.sendLastChunkFlag = false;
            this.readLength.set(0);
            this.chunkByteBuf = null;
            this.lastHttpContent = null;
        }
    }
}
