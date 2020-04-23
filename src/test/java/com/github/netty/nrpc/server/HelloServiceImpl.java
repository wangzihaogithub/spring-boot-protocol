package com.github.netty.nrpc.server;

import com.github.netty.nrpc.api.NRpcHelloService;
import org.springframework.stereotype.Component;

@Component
public class HelloServiceImpl implements NRpcHelloService {
    @Override
    public String sayHello(String name) {
        return "hi! " + name;
    }
}
