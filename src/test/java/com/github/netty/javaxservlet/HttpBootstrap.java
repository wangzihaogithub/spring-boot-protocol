package com.github.netty.javaxservlet;

import com.github.netty.StartupServer;
import com.github.netty.javaxservlet.example.MyHttpServlet;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;

public class HttpBootstrap {

    public static void main(String[] args) {
        StartupServer server = new StartupServer(80);
        server.addProtocol(newHttpProtocol());
        server.start();
    }

    private static HttpServletProtocol newHttpProtocol() {
        ServletContext servletContext = new ServletContext();
        servletContext.setDocBase(System.getProperty("user.dir"), "/webapp");
        servletContext.addServlet("myHttpServlet", new MyHttpServlet())
                .addMapping("/test");
        return new HttpServletProtocol(servletContext);
    }
}
