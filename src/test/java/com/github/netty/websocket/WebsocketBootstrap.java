package com.github.netty.websocket;

import com.github.netty.nrpc.client.NRpcClientBootstrap;
import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URL;

/**
 * Websocket server
 * @author wangzihao
 */
@EnableNettyEmbedded
@SpringBootApplication
public class WebsocketBootstrap {
    private static final URL CONFIG_URL = NRpcClientBootstrap.class.getResource(
            "/websocket/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL);
        SpringApplication.run(NRpcClientBootstrap.class,args);
    }

}
