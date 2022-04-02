package com.github.netty.mysql;

import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URL;

@EnableNettyEmbedded
@SpringBootApplication
public class MysqlBootstrap {
    private static final URL CONFIG_URL = MysqlBootstrap.class.getResource(
            "/mysql/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL.toString());
        SpringApplication.run(MysqlBootstrap.class,args);
    }

}
