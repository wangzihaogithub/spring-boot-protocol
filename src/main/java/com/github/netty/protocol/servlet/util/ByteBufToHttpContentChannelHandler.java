package com.github.netty.protocol.servlet.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;

@ChannelHandler.Sharable
public class ByteBufToHttpContentChannelHandler extends ChannelOutboundHandlerAdapter {
    public static final ByteBufToHttpContentChannelHandler INSTANCE = new ByteBufToHttpContentChannelHandler();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            // convert ByteBuf to HttpContent to make it work with compression. This is needed as we use the
            // ChunkedWriteHandler to send files when compression is enabled.
            ByteBuf buff = (ByteBuf) msg;
            if (buff.isReadable()) {
                // We only encode non empty buffers, as empty buffers can be used for determining when
                // the content has been flushed and it confuses the HttpContentCompressor
                // if we let it go
                msg = new DefaultHttpContent(buff);
            }
        }
        super.write(ctx, msg, promise);
    }
}
    