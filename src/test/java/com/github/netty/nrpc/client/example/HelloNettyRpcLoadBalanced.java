package com.github.netty.nrpc.client.example;

import com.github.netty.springboot.client.NettyRpcLoadBalanced;
import com.github.netty.springboot.client.NettyRpcRequest;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

public class HelloNettyRpcLoadBalanced implements NettyRpcLoadBalanced {
    private final List<InetSocketAddress> remoteAddressList;
    private final Random random = new Random();
    public HelloNettyRpcLoadBalanced(List<InetSocketAddress> remoteAddressList) {
        this.remoteAddressList = remoteAddressList;
    }

    @Override
    public InetSocketAddress chooseAddress(NettyRpcRequest request) {
        return remoteAddressList.get(random.nextInt(remoteAddressList.size()));
    }
}
