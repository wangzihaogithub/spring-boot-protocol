package com.github.netty.springdubbo;

import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URL;

@EnableNettyEmbedded
@SpringBootApplication
public class DubboBootstrap {
    private static final URL CONFIG_URL = DubboBootstrap.class.getResource(
            "/springdubbo/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL.toString());
        SpringApplication.run(DubboBootstrap.class,args);
    }

}
