package com.github.netty.springboot.client;

import com.github.netty.core.util.ApplicationX;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.springboot.NettyProperties;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Information about the RPC request
 * @author wangzihao
 */
public interface NettyRpcRequest {
    /**
     * rpcInstanceKey {@link RpcClient#getRpcInstance(String)}
     * @return rpcInstanceKey
     */
    String getRpcInstanceKey();
    Object getProxy();
    NettyRpcClientProxy getClientProxy();
    Supplier<NettyRpcLoadBalanced> getLoadBalancedSupplier();

    /**
     * The method to call this time
     * @return Method
     */
    Method getMethod();
    /**
     * Parameters for this call
     * @return args
     */
    Object[] getArgs();
    /**
     * It is obtained from the serviceName field annotated by the NettyRpcClient
     * @return The service id
     */
    String getServiceName();
    /**
     * The RequestMapping annotation is retrieved from the value field of the RequestMapping annotation. If the RequestMapping annotation is not typed, the default is a lowercase method name
     * @return requestMappingName
     */
    String getRequestMappingName();

    /**
     * you rpc service version {@link com.github.netty.annotation.Protocol.RpcService#version()}
     * @return any string
     */
    String getVersion();

    /**
     * setting once request timeout. unit is millSecond
     * @param timeout timeout
     */
    void setTimeout(long timeout);
    long getTimeout();

    /**
     * Yml configuration file
     * @return NettyProperties
     */
    NettyProperties getNettyProperties();
    /**
     * Application bean container
     * @return ApplicationX
     */
    ApplicationX getApplication();
    /**
     * address mapping client instance
     * @return clients
     */
    Map<InetSocketAddress, RpcClient> getClientMap();
    /**
     * Get interface class
     * @return interfaceClass
     */
    Class getInterfaceClass();

}
