package com.github.netty.springboot.client;

import com.github.netty.springboot.NettyProperties;

import java.lang.reflect.Method;

/**
 * RPC请求的信息
 * @author 84215
 */
public interface NettyRpcRequest {
    /**
     * 本次调用的方法
     * @return
     */
    Method getMethod();
    /**
     * 本次调用的参数
     * @return
     */
    Object[] getArgs();
    /**
     * 是从NettyRpcClient注解的serviceId字段获取的
     * @return 服务id
     */
    String getServiceId();
    /**
     * 是从RequestMapping注解的value字段获取的, 如果没有打RequestMapping注解, 默认是首字母小写的方法名称
     * @return
     */
    String getServiceName();
    /**
     * yml的配置文件, 可以动态修改，动态变化
     * @return
     */
    NettyProperties getNettyProperties();
    /**
     * 接口
     * @return
     */
    Class getInterfaceClass();

}
