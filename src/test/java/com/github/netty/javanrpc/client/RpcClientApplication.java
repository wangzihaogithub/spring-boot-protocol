package com.github.netty.javanrpc.client;

import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcClientCompletableFuture;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RpcClientApplication {

    public static void main(String[] args) {
        RpcClient rpcClient = new RpcClient("localhost", 80);

        DemoMessageClient demoMessageClient = rpcClient.newInstance(DemoMessageClient.class);
        DemoClient demoClient = rpcClient.newInstance(DemoClient.class);
        DemoAsyncClient demoAsyncClient = rpcClient.newInstance(DemoAsyncClient.class);
        DemoStreamClient demoStreamClient = rpcClient.newInstance(DemoStreamClient.class);

        try {
            demoMessageClient.hello("wang");

            // 仅仅发一个消息
            Map result = demoClient.hello("wang");
            System.out.println("result = " + result);

            // 一问多答
            demoStreamClient.hello("wang").whenChunk(chunk -> {
                System.out.println("chunk = " + chunk);
            }).whenComplete((data, exception) -> {
                System.out.println("data = " + data);
                System.out.println("exception = " + exception);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoClient {
        Map hello(@NRpcParam("name") String name);
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoAsyncClient {
        CompletableFuture<Map> hello(@NRpcParam("name") String name);
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoMessageClient {
        // 仅仅发一个消息. only send a message. not need to wait peer server for a reply
        void hello(@NRpcParam("name") String name);
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoStreamClient {
        RpcClientCompletableFuture<Map, Object> hello(@NRpcParam("name") String name);
    }

}
