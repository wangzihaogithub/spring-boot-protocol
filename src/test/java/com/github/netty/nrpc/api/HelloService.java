package com.github.netty.nrpc.api;

import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/hello")
public interface HelloService {
    HelloData sayHello(String name, Integer id, Boolean name3, HelloDTO request);
    HelloData sayHello1(String name);
}
