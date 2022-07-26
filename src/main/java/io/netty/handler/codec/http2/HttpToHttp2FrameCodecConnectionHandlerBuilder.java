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

import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class HttpToHttp2FrameCodecConnectionHandlerBuilder extends
        AbstractHttp2ConnectionHandlerBuilder<Http2ConnectionHandler, HttpToHttp2FrameCodecConnectionHandlerBuilder> {

    private boolean compressor = true;

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder connection(Http2Connection connection) {
        return super.connection(connection);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder frameListener(Http2FrameListener frameListener) {
        return super.frameListener(frameListener);
    }

    @Override
    public HttpToHttp2FrameCodecConnectionHandlerBuilder frameLogger(Http2FrameLogger frameLogger) {
        return super.frameLogger(frameLogger);
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
                decoupleCloseAndGoAway(), isValidateHeaders());
    }
}
