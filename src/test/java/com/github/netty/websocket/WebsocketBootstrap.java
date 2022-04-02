package com.github.netty.websocket;

import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.net.URL;

/**
 * Websocket server
 * 访问 http://localhost:8080/index.html 可以看效果
 *
 * @author wangzihao
 */
@EnableWebSocket
@EnableNettyEmbedded
@SpringBootApplication
public class WebsocketBootstrap {
    private static final URL CONFIG_URL = WebsocketBootstrap.class.getResource(
            "/websocket/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location", CONFIG_URL.toString());
        SpringApplication.run(WebsocketBootstrap.class, args);
    }

}
