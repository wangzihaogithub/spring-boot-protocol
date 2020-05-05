package com.github.netty.nrpc.client;

import com.github.netty.nrpc.client.example.HelloNettyRpcLoadBalanced;
import com.github.netty.springboot.EnableNettyEmbedded;
import com.github.netty.springboot.EnableNettyRpcClients;
import com.github.netty.springboot.client.NettyRpcLoadBalanced;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

//@EnableNettyEmbedded
@EnableNettyRpcClients
@SpringBootApplication
public class NRpcClientBootstrap {
    private static final URL CONFIG_URL = NRpcClientBootstrap.class.getResource(
            "/nrpc/client/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL);
        SpringApplication.run(NRpcClientBootstrap.class,args);
    }

    /**
     * 必须实现这个类, 不然 RPC客户端无法运作
     * @return 返回一个IP地址
     */
    @Bean
    public NettyRpcLoadBalanced nettyRpcLoadBalanced(){
        List<InetSocketAddress> list = new ArrayList<>();
        list.add(new InetSocketAddress("localhost", 8080));
        list.add(new InetSocketAddress("localhost", 8080));
        list.add(new InetSocketAddress("localhost", 8080));
        return new HelloNettyRpcLoadBalanced(list);
    }
}
