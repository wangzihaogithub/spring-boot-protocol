/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.http2;

import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.util.internal.UnstableApi;

/**
 * Builder which builds {@link HttpToHttp2FrameCodec} objects.
 */
@UnstableApi
public final class HttpToHttp2FrameCodecConnectionHandlerBuilder extends
        AbstractHttp2ConnectionHandlerBuilder<Http2ConnectionHandler, HttpToHttp2FrameCodecConnectionHandlerBuilder> {

    private HttpScheme httpScheme;
    private boolean compressor = true;

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder validateHeaders(boolean validateHeaders) {
        return super.validateHeaders(validateHeaders);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder initialSettings(Http2Settings settings) {
        return super.initialSettings(settings);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder frameListener(Http2FrameListener frameListener) {
        return super.frameListener(frameListener);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        return super.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder server(boolean isServer) {
        return super.server(isServer);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder connection(Http2Connection connection) {
        return super.connection(connection);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder codec(Http2ConnectionDecoder decoder,
                                                               Http2ConnectionEncoder encoder) {
        return super.codec(decoder, encoder);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder frameLogger(Http2FrameLogger frameLogger) {
        return super.frameLogger(frameLogger);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder encoderEnforceMaxConcurrentStreams(
            boolean encoderEnforceMaxConcurrentStreams) {
        return super.encoderEnforceMaxConcurrentStreams(encoderEnforceMaxConcurrentStreams);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder headerSensitivityDetector(
            SensitivityDetector headerSensitivityDetector) {
        return super.headerSensitivityDetector(headerSensitivityDetector);
    }

    @Override
    @Deprecated
    public HttpToHttp2FrameCodecConnectionHandlerBuilder initialHuffmanDecodeCapacity(int initialHuffmanDecodeCapacity) {
        return super.initialHuffmanDecodeCapacity(initialHuffmanDecodeCapacity);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        return super.decoupleCloseAndGoAway(decoupleCloseAndGoAway);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder flushPreface(boolean flushPreface) {
        return super.flushPreface(flushPreface);
    }

    /**
     * Add {@code scheme} in {@link Http2Headers} if not already present.
     *
     * @param httpScheme {@link HttpScheme} type
     * @return {@code this}.
     */
    public HttpToHttp2FrameCodecConnectionHandlerBuilder httpScheme(HttpScheme httpScheme) {
        this.httpScheme = httpScheme;
        return self();
    }

    public HttpToHttp2FrameCodecConnectionHandlerBuilder compressor(boolean compressor) {
        this.compressor = compressor;
        return self();
    }

    @Override
    public Http2ConnectionHandler build() {
        return super.build();
    }

    @Override
    protected Http2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                          Http2Settings initialSettings) {
        if (compressor) {
            encoder = new CompressorHttp2ConnectionEncoder(encoder);
        }
        return new HttpToHttp2ConnectionHandler(decoder, encoder, initialSettings,
                decoupleCloseAndGoAway(), flushPreface(), isValidateHeaders(), httpScheme);
//        return new HttpToHttp2FrameCodec(encoder, decoder, initialSettings,
//                decoupleCloseAndGoAway(), flushPreface(), isValidateHeaders(), httpScheme); todo 目前不能并行响应, 需要实现一个同时支持h1包与streamId隔离write的类 HttpToHttp2FrameCodec
    }
}
