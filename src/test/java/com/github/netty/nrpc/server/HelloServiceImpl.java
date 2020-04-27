package com.github.netty.nrpc.server;

import com.github.netty.annotation.Protocol;
import com.github.netty.nrpc.api.HelloService;
import com.github.netty.nrpc.api.HelloRequest;
import com.github.netty.nrpc.api.HelloResponse;

@Protocol.RpcService
public class HelloServiceImpl implements HelloService {

    @Override
    public HelloResponse sayHello(String name, Integer id, Boolean bool, HelloRequest request) {
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        System.out.printf("name=%s,id=%d,bool=%s,request=%s\n",
                name,id,bool,request);
        HelloResponse response = new HelloResponse();
        response.setSay("hi! " + name);
        return response;
    }
}
