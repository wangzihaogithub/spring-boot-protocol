package com.github.netty.springdubbo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URL;

public class Server {
    private static final URL CONFIG_URL = Server.class.getResource(
            "/springdubbo/dubbo-server.xml");

    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext(CONFIG_URL.toString());
        classPathXmlApplicationContext.start();
        System.in.read();
    }
}
