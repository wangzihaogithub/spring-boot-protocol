package com.github.netty.nrpc;

import com.github.netty.springboot.EnableNettyRpcClients;
import com.github.netty.springboot.client.NettyRpcLoadBalanced;
import com.github.netty.springboot.client.NettyRpcRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.InetSocketAddress;
import java.net.URL;

@EnableNettyRpcClients
@SpringBootApplication
public class NRpcClientBootstrap {
    private static final URL CONFIG_URL = NRpcClientBootstrap.class.getResource(
            "/nrpc/client/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL);
        SpringApplication.run(NRpcClientBootstrap.class,args);
    }

    @Bean
    public NettyRpcLoadBalanced nettyRpcLoadBalanced(){
        return new NettyRpcLoadBalanced(){
            @Override
            public InetSocketAddress chooseAddress(NettyRpcRequest request) {
                return new InetSocketAddress("localhost",8080);
            }
        };
    }
}
