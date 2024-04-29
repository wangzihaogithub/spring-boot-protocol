package com.github.netty.javadubbo;

import com.github.netty.StartupServer;
import com.github.netty.protocol.dubbo.DubboPacket;
import com.github.netty.protocol.dubbo.DubboProtocol;
import com.github.netty.protocol.dubbo.ProxyFrontendHandler;

import java.net.InetSocketAddress;

/**
 * dubbo proxy server
 * 访问 http://localhost:8080/index.html 可以看效果
 * <p>
 * byte 16
 * 0-1 magic code
 * 2 flag
 * 8 - 1-request/0-response
 * 7 - two way
 * 6 - heartbeat
 * 1-5 serialization id
 * 3 status
 * 20 ok
 * 90 error?
 * 4-11 id (long)
 * 12 -15 datalength
 *
 * @author wangzihao
 */
public class DubboProxyBootstrap {

    public static void main(String[] args) {
        StartupServer server = new StartupServer(20880);
        server.addProtocol(newDubboProtocol());
        server.start();
    }

    private static DubboProtocol newDubboProtocol() {
        return new DubboProtocol(() -> {
            ProxyFrontendHandler handler = new ProxyFrontendHandler() {
                @Override
                public String getBackendServiceName(DubboPacket packet) {
                    System.out.println("getBackendServiceName = " + packet);
                    return super.getBackendServiceName(packet);
                }
            };
            handler.putServiceAddress("pay-service", new InetSocketAddress("127.0.0.1", 20881));
            handler.putServiceAddress("order-service", new InetSocketAddress("127.0.0.1", 20881));
            handler.setDefaultServiceName("pay-service");
            return handler;
        });
    }

}