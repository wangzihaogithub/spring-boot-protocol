package com.github.netty.nrpc.client;

import com.github.netty.nrpc.api.HelloData;
import com.github.netty.springboot.NettyRpcClient;
import org.reactivestreams.Publisher;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.CompletableFuture;

@NettyRpcClient(serviceName = "nrpc-server",timeout = 100)
@RequestMapping("/hello")
public interface HelloAsyncClient{
    @RequestMapping("sayHello")
    Publisher<HelloData> sayHelloByTest(String name, int id);

    CompletableFuture<HelloData> sayHello1(String name, int id);

}
