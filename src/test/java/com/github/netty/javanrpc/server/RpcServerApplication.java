package com.github.netty.javanrpc.server;

import com.github.netty.StartupServer;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.NRpcProtocol;
import com.github.netty.protocol.nrpc.RpcEmitter;
import com.github.netty.protocol.servlet.DefaultServlet;
import com.github.netty.protocol.servlet.ServletContext;

import java.util.LinkedHashMap;
import java.util.Map;

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
        public RpcEmitter<Map, Integer> hello(String name) {
            Map result = new LinkedHashMap();
            result.put("name", name);
            result.put("timestamp", System.currentTimeMillis());
            System.out.println("result = " + result);

            RpcEmitter<Map, Integer> emitter = new RpcEmitter<>();
            new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    emitter.send(i);
                }
                emitter.complete(result);
            }).start();
            return emitter;
        }
    }
}