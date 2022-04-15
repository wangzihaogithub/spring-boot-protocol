package com.github.netty.javanrpc.client;

import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;
import com.github.netty.protocol.nrpc.RpcClient;

import java.util.Map;

public class RpcClientApplication {

    public static void main(String[] args){
        RpcClient rpcClient = new RpcClient("localhost", 80);
        DemoClient demoClient = rpcClient.newInstance(DemoClient.class);
        Map result = demoClient.hello("wang");

        System.out.println("result = " + result);
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoClient {
        Map hello(@NRpcParam("name") String name);
    }

}
