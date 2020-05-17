package com.github.netty.nrpc.client;

import com.github.netty.nrpc.api.HelloData;
import com.github.netty.springboot.NettyRpcClient;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import org.springframework.web.bind.annotation.RequestMapping;

@NettyRpcClient(serviceName = "nrpc-server",timeout = 100)
@RequestMapping("/hello")
public interface HelloRxjava3AsyncClient {
    Observable<HelloData> sayHello(String name, int id);
    Flowable<HelloData> sayHello1(String name);
}
