package com.github.netty.springboot.client;

import java.net.InetSocketAddress;

/**
 * Load balancing
 * @author wangzihao
 */
@FunctionalInterface
public interface NettyRpcLoadBalanced {

    /**
     * Pick an IP address
     * @param request request
     * @return The IP address
     */
    InetSocketAddress chooseAddress(NettyRpcRequest request);

}
