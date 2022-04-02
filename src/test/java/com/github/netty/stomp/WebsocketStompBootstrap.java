package com.github.netty.stomp;

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
public class WebsocketStompBootstrap {
    private static final URL CONFIG_URL = WebsocketStompBootstrap.class.getResource(
            "/stomp/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL.toString());
        SpringApplication.run(WebsocketStompBootstrap.class,args);
    }

}
