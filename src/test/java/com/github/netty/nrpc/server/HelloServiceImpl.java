package com.github.netty.nrpc.server;

import com.github.netty.annotation.Protocol;
import com.github.netty.nrpc.api.HelloService;
import com.github.netty.nrpc.api.HelloDTO;
import com.github.netty.nrpc.api.HelloData;

@Protocol.RpcService
public class HelloServiceImpl implements HelloService {

    @Override
    public HelloData sayHello(String name, Integer id, Boolean bool, HelloDTO request) {
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        System.out.printf("sayHello name=%s,id=%d,bool=%s,request=%s\n",
                name,id,bool,request);
        HelloData response = new HelloData();
        response.setSay("hi! " + name);
        return response;
    }

    @Override
    public HelloData sayHello1(String name) {
        System.out.printf("sayHello1 name=%s\n",
                name);
        HelloData response = new HelloData();
        response.setSay("hi! " + name);
        return response;
    }
}
