package com.github.netty.javanrpc.client;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcClientChunkCompletableFuture;

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
            // 默认
            Map map = demoClient.hello("123");
            System.out.println("默认结果的data = " + map);

            // 交互式接口, 一问多答
            demoStreamClient.hello("wang").whenChunkAck((chunk, index, ack) -> {
                System.out.println("回答 chunk = " + chunk);
                boolean hasNext = index < 100;
                ack.ack(hasNext);
            }).whenComplete((data, exception) -> {
                System.out.println("最终结果的data = " + data);
                System.out.println("最终结果的exception = " + exception);
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

        @NRpcMethod(value = "method1", timeout = 6000)
        CompletableFuture<Map> method1(@NRpcParam("name") String name);
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoMessageClient {
        // 仅仅发一个消息. only send a message. not need to wait peer server for a reply
        void hello(@NRpcParam("name") String name);
    }

    @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
    public interface DemoStreamClient {
        RpcClientChunkCompletableFuture<Map, Object> hello(@NRpcParam("name") String name);
    }

}
