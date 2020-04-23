package com.github.netty.http;

import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URL;

@EnableNettyEmbedded
@SpringBootApplication
public class HttpBootstrap {
    private static final URL CONFIG_URL = HttpBootstrap.class.getResource(
            "/http/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL);
        SpringApplication.run(HttpBootstrap.class,args);
    }

}
