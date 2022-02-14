package com.github.netty.nrpc.server;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcService;
import com.github.netty.nrpc.api.HelloDTO;
import com.github.netty.nrpc.api.HelloData;
import com.github.netty.nrpc.api.HelloService;

@NRpcService
public class HelloServiceImpl implements HelloService {

    @NRpcMethod(timeoutInterrupt = true, timeout = 0)
    @Override
    public HelloData sayHello(String name, Integer id, Boolean bool, HelloDTO request) {
        int i = 0;
        while (i++ < 10) {
//            try {
//                Thread.sleep(10000);
//            } catch (InterruptedException e) {
//            }
        }
        System.out.printf("sayHello name=%s,id=%d,bool=%s,request=%s\n",
                name, id, bool, request);
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
