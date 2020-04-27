package com.github.netty.nrpc.server;

import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URL;

@EnableNettyEmbedded
@SpringBootApplication
public class NRpcServerBootstrap {
    private static final URL CONFIG_URL = NRpcServerBootstrap.class.getResource(
            "/nrpc/server/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL);
        SpringApplication.run(NRpcServerBootstrap.class,args);
    }

}
