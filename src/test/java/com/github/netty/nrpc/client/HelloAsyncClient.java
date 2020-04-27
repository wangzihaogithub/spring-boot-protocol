package com.github.netty.nrpc.client;

import com.github.netty.nrpc.api.HelloResponse;
import com.github.netty.springboot.NettyRpcClient;
import org.reactivestreams.Publisher;
import org.springframework.web.bind.annotation.RequestMapping;

@NettyRpcClient(serviceImplName = "nrpc-server",timeout = 100)
@RequestMapping("/hello")
public interface HelloAsyncClient{
    Publisher<HelloResponse> sayHello(String name, int id);
}
