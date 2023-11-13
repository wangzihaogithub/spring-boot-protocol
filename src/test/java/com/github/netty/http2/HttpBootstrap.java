package com.github.netty.http2;

import com.github.netty.StartupServer;
import com.github.netty.javaxservlet.example.MyHttpServlet;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;

import java.io.File;

public class HttpBootstrap {

    public static void main(String[] args) throws Exception {
        StartupServer server = new StartupServer(80);
//        StartupServer server = new StartupServer(443);
        server.addProtocol(newHttpProtocol());
        server.start();
    }

    private static HttpServletProtocol newHttpProtocol() throws Exception {
        ServletContext servletContext = new ServletContext();
        servletContext.setDocBase(System.getProperty("user.dir"), "/webapp");
        servletContext.addServlet("myHttpServlet", new MyHttpServlet())
                .addMapping("/test");
        HttpServletProtocol protocol = new HttpServletProtocol(servletContext);
        protocol.setEnableH2c(true);

//        protocol.setSslFileJks(
//                new File("G:\\githubs\\spring-boot-protocol\\webapp\\mydomain.com.jks"),
//                new File("G:\\githubs\\spring-boot-protocol\\webapp\\jks-password.txt")
//        );
//        protocol.setSslFileCrtPem(crtFile, pemFile);
        return protocol;
    }

}
