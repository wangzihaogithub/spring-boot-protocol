package com.github.netty.http2;

import com.github.netty.protocol.servlet.http2.NettyHttp2Client;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;

import javax.net.ssl.SSLException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class Http2Tests {

    public static void main(String[] args) throws Exception {
        NettyHttp2Client http2Client = new NettyHttp2Client("http://localhost")
                .logger(LogLevel.INFO)
                .awaitConnect();
        for (int i = 0; i < 1; i++) {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "/test", Unpooled.EMPTY_BUFFER);
            http2Client.writeAndFlush(request).onSuccess(e -> {
                System.out.println(e);
            });
        }

        List<NettyHttp2Client.H2Response> httpPromises = http2Client.flush().get();
        httpPromises.forEach(NettyHttp2Client.H2Response::close);

        Long closeTime = http2Client.close(true).get();
        System.out.printf("connectTime = %d, closeTime = %d \n",
                http2Client.getEndConnectTimestamp() - http2Client.getBeginConnectTimestamp(), closeTime);
    }

}
