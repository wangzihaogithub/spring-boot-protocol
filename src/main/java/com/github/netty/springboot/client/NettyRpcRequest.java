package com.github.netty.springboot.client;

import com.github.netty.springboot.NettyProperties;

import java.lang.reflect.Method;

/**
 * Information about the RPC request
 * @author wangzihao
 */
public interface NettyRpcRequest {
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
     * Yml configuration file
     * @return NettyProperties
     */
    NettyProperties getNettyProperties();
    /**
     * Get interface class
     * @return interfaceClass
     */
    Class getInterfaceClass();

}
