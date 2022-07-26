package com.github.netty.http2;

import com.github.netty.protocol.servlet.http2.NettyHttp2Client;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.logging.LogLevel;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Arrays;
import java.util.List;

public class Http2Tests {

    public static void main(String[] args) throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .protocols(Arrays.asList(Protocol.H2_PRIOR_KNOWLEDGE))
                .build();

        Request build = new Request.Builder()
                .url("http://localhost/test")
                .build();
        Response execute = okHttpClient.newCall(build).execute();
        String string = execute.body().string();
        System.out.println("execute = " + execute);

        NettyHttp2Client http2Client = new NettyHttp2Client("http://localhost")
                .logger(LogLevel.INFO)
                .awaitConnect();
        for (int i = 0; i < 1; i++) {
            http2Client.writeAndFlush(HttpMethod.GET, "/test", Unpooled.EMPTY_BUFFER).onSuccess(e -> {
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
