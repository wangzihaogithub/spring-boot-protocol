package com.github.netty.nrpc.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HttpController {
    @Autowired
    private HelloClient helloClient;

    @RequestMapping("/sayHello")
    public String sayHello(String name){
        return helloClient.sayHello(name);
    }

}
