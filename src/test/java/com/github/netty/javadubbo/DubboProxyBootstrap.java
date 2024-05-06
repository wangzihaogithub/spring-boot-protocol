package com.github.netty.javadubbo;

import com.github.netty.StartupServer;
import com.github.netty.protocol.DubboProtocol;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.dubbo.DubboPacket;
import com.github.netty.protocol.dubbo.ProxyFrontendHandler;
import com.github.netty.protocol.servlet.ServletContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Supplier;

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
        server.addProtocol(newHttpProtocol());
        server.start();
        System.out.println("浏览器访问Dubbo代理监控 ：  http://127.0.0.1:20880");
    }

    private static DubboProtocol newDubboProtocol() {
        Supplier<ProxyFrontendHandler> proxySupplier = () -> {
            ProxyFrontendHandler proxy = new ProxyFrontendHandler() {
                @Override
                public String getBackendServiceName(DubboPacket packet) {
                    return packet.getAttachmentValue("remote.application");
                }
            };
            proxy.putServiceAddress("pay-service", new InetSocketAddress("127.0.0.1", 20881));
            proxy.putServiceAddress("order-service", new InetSocketAddress("127.0.0.1", 20881));
            proxy.setDefaultServiceName("pay-service");
            return proxy;
        };
        return new DubboProtocol(proxySupplier);
    }

    private static HttpServletProtocol newHttpProtocol() {
        ServletContext servletContext = new ServletContext();
        servletContext.setDocBase(System.getProperty("user.dir"), "/webapp");
        servletContext.addServlet("myHttpServlet", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        List<ProxyFrontendHandler> activeList = ProxyFrontendHandler.getActiveList();
                        resp.getWriter().write(activeList.toString());
                    }
                })
                .addMapping("/");
        return new HttpServletProtocol(servletContext);
    }
}