package com.github.netty.http2;

import com.github.netty.protocol.servlet.http2.NettyHttp2Client;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Http2Tests.class)
public class Http2Tests {

    @Test
    public void test() throws IOException, ExecutionException, InterruptedException {
        NettyHttp2Client http2Client = new NettyHttp2Client("https://maimai.cn")
                .logger(LogLevel.INFO)
                .maxPendingSize(550000);

        for (int i = 0; i < 550000; i++) {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "/sdk/company/is_admin", Unpooled.EMPTY_BUFFER);
            http2Client.write(request).onSuccess(FullHttpResponse::release);
        }

        List<NettyHttp2Client.H2Response> httpPromises = http2Client.flush().get();
        httpPromises.forEach(NettyHttp2Client.H2Response::close);

        Long closeTime = http2Client.close(true).get();
        System.out.printf("connectTime = %d, closeTime = %d \n",
                http2Client.getEndConnectTimestamp() - http2Client.getBeginConnectTimestamp(), closeTime);
    }

}
