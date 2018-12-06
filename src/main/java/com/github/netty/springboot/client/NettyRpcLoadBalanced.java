package com.github.netty.springboot.client;

import java.net.InetSocketAddress;

/**
 * 负载均衡
 * @author 84215
 */
@FunctionalInterface
public interface NettyRpcLoadBalanced {

    /**
     * 挑选一个IP地址
     * @param request 请求
     * @return IP地址
     */
    InetSocketAddress chooseAddress(NettyRpcRequest request);

}
