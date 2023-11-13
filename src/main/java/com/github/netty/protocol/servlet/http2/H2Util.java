package com.github.netty.protocol.servlet.http2;

import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AsciiString;

public class H2Util {

    public static Http2ConnectionHandler newHttp2Handler(LogLevel logLevel,
                                                         int http2MaxReservedStreams, int maxContentLength, boolean enableContentCompression) {
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true, http2MaxReservedStreams);
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
                .propagateSettings(false)
                .validateHttpHeaders(true)
                .maxContentLength(maxContentLength)
                .build();

        HttpToHttp2FrameCodecConnectionHandlerBuilder build = new HttpToHttp2FrameCodecConnectionHandlerBuilder()
                .frameListener(listener)
                .connection(connection)
                .compressor(enableContentCompression);
        if (logLevel != null) {
            build.frameLogger(new Http2FrameLogger(logLevel));
        }
        return build.build();
    }

    public static HttpServerUpgradeHandler.UpgradeCodecFactory newUpgradeCodecFactory(LogLevel logLevel, int http2MaxReservedStreams, int maxContentLength, boolean enableContentCompression) {
        return new HttpServerUpgradeHandler.UpgradeCodecFactory() {
            @Override
            public HttpServerUpgradeHandler.UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                    return new Http2ServerUpgradeCodec(newHttp2Handler(logLevel, http2MaxReservedStreams, maxContentLength, enableContentCompression));
                } else {
                    return null;
                }
            }
        };
    }

}
