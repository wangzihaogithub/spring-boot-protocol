package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpScheme;

public class HttpToHttp2FrameCodec extends Http2FrameCodec {
    private final HttpToHttp2ConnectionHandler httpToHttp2ConnectionHandler;

    HttpToHttp2FrameCodec(Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder, Http2Settings initialSettings,
                          boolean decoupleCloseAndGoAway, boolean flushPreface,
                          boolean validateHeaders, HttpScheme httpScheme) {
        super(encoder, decoder, initialSettings, decoupleCloseAndGoAway, flushPreface);
        httpToHttp2ConnectionHandler = new HttpToHttp2ConnectionHandler(decoder, encoder, initialSettings, validateHeaders, decoupleCloseAndGoAway, httpScheme);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof HttpMessage || msg instanceof HttpContent) {
            httpToHttp2ConnectionHandler.write(ctx, msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }
}
