package com.github.netty.javanrpc.server;

import com.github.netty.StartupServer;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.NRpcProtocol;
import com.github.netty.protocol.nrpc.RpcEmitter;
import com.github.netty.protocol.servlet.DefaultServlet;
import com.github.netty.protocol.servlet.ServletContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RpcServerApplication {

    public static void main(String[] args) {
        StartupServer server = new StartupServer(80);
        server.addProtocol(newHttpProtocol());
        server.addProtocol(newRpcMessageProtocol());
        server.start();
    }

    private static HttpServletProtocol newHttpProtocol() {
        ServletContext servletContext = new ServletContext();
//        servletContext.setDocBase("D://demo", "/webapp");
        servletContext.setDocBase(System.getProperty("user.dir"), "/webapp");
        servletContext.addServlet("myServlet", new DefaultServlet()).addMapping("/");
        return new HttpServletProtocol(servletContext);
    }

    private static NRpcProtocol newRpcMessageProtocol() {
        ApplicationX applicationX = new ApplicationX();
        applicationX.scanner(true, "com.github.netty.javanrpc.server")
                .inject();
        return new NRpcProtocol(applicationX);
    }

    @ApplicationX.Component
    @NRpcService(value = "/demo", version = "1.0.0")
    public static class DemoService {
        public RpcEmitter<Map, String> hello(String name) {
            System.out.println("hello = " + name);

            RpcEmitter<Map, String> emitter = new RpcEmitter<>();
            send(emitter, 0);
            return emitter;
        }

        private String selectById(Integer index) {
            return "server的数据块_data_" + index;
        }

        private void send(RpcEmitter<Map, String> emitter, int index) {
            String data = selectById(index);
            System.out.println("send = " + index);
            emitter.send(data, Boolean.class, 1000).whenComplete((next, err) -> {
                if (err != null) {
                    // 超时异常
                    System.out.println("err = " + err);
                    emitter.complete(err);
                } else if (next == null) {
                    // 客户端只要结果, 不要过程.
                    Map<String, Object> result = new HashMap<>();
                    result.put("msg", "完毕1");
                    System.out.println("result = 完毕");
                    emitter.complete(result);
                } else if (next) {
                    // 客户端要过程.
                    send(emitter, index + 1);
                } else {
                    // 客户端过程处理完了
                    Map<String, Object> result = new HashMap<>();
                    result.put("msg", "完毕");
                    System.out.println("result = 完毕");
                    emitter.complete(result);
                }
            }).exceptionally(e -> {
                System.out.println("e = " + e);
                return false;
            });
        }

        public CompletableFuture<Map> method1(String name) {
            CompletableFuture<Map> future = new CompletableFuture<>();
            new Thread(() -> {
                try {
                    Map result = new LinkedHashMap();
                    result.put("name", name);
                    result.put("timestamp", System.currentTimeMillis());
                    System.out.println("result = " + result);

                    Thread.sleep(5000);
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            return future;
        }
    }
}