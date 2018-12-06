package com.github.netty.register.servlet;

import com.github.netty.core.util.HttpHeaderUtil;
import com.github.netty.core.util.IOUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * servlet 输出流 (分段传输)
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 * @author 84215
 */
@sun.misc.Contended
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
     * 刷新缓冲区 (可以监听完成事件)
     * @param listener 刷新缓冲区后调用
     * @throws IOException
     */
    private void flush0(ChannelFutureListener listener) throws IOException {
        try {
            ByteBuf content = lockBuffer();
            if (content != null) {
                this.chunkedInput.setChunkByteBuf(content);
                setBuffer(null);
            }
        }finally {
            unlockBuffer();
        }

        if (super.isSendResponseHeader.compareAndSet(false,true)) {
            NettyHttpResponse nettyResponse = getHttpServletObject().getHttpServletResponse().getNettyResponse();
            LastHttpContent lastHttpContent = nettyResponse.enableTransferEncodingChunked();
            chunkedInput.setLastHttpContent(lastHttpContent);

            super.sendResponse(future -> {
                ChannelHandlerContext channel = getHttpServletObject().getChannelHandlerContext();
                ChannelPromise promise;
                if(listener == null){
                    promise = channel.voidPromise();
                }else {
                    promise = channel.newProgressivePromise();
                    promise.addListener(listener);
                }

//                channel.writeAndFlush(new HttpChunkedInput(new ChunkedFile(
//                        new File("C:\\Users\\acer01\\Desktop\\开发工具1\\gz.sql"))),promise);
                channel.writeAndFlush(chunkedInput,promise);
            });
            return;
        }

        getHttpServletObject().getChannelHandlerContext().flush();
        if(listener != null){
            try{
                listener.operationComplete(null);
            } catch (Exception e) {
                throw new IOException(e.getMessage(),e);
            }
        }
    }

    /**
     * 字节块输入
     */
    class ByteChunkedInput implements ChunkedInput<Object>{
        private boolean closeInputFlag = false;
        private boolean sendLastChunkFlag = false;
        private AtomicInteger readLength = new AtomicInteger();
        private ByteBuf chunkByteBuf;
        private LastHttpContent lastHttpContent;

        public ByteChunkedInput() {
        }

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
            readLength = null;
        }

        @Deprecated
        @Override
        public Object readChunk(ChannelHandlerContext ctx) throws Exception {
            return readChunk(ctx.alloc());
        }

        @Override
        public Object readChunk(ByteBufAllocator allocator) throws Exception {
            if (closeInputFlag) {
                if (sendLastChunkFlag) {
                    return null;
                } else {
                    // Send last chunk for this input
                    sendLastChunkFlag = true;
                    //移除头部不支持拖挂的字段
                    HttpHeaderUtil.removeHeaderUnSupportTrailer(lastHttpContent);
                    return lastHttpContent;
                }
            }else {
                if (chunkByteBuf == null) {
                    return null;
                }
                ByteBuf byteBuf = chunkByteBuf;
                chunkByteBuf = null;
                readLength.addAndGet(byteBuf.capacity());
                return byteBuf;
            }
        }

        public void setLastHttpContent(LastHttpContent lastHttpContent) {
            this.lastHttpContent = lastHttpContent;
        }

        public void setCloseInputFlag(boolean isCloseInput) {
            this.closeInputFlag = isCloseInput;
        }

        public void setChunkByteBuf(ByteBuf chunkByteBuf) {
            //切换读模式
            IOUtil.writerModeToReadMode(chunkByteBuf);
            this.chunkByteBuf = chunkByteBuf;
        }
    }
}
