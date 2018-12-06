package com.github.netty.springboot.client;

import com.github.netty.core.util.ThreadPoolX;
import com.github.netty.register.rpc.exception.RpcException;
import com.github.netty.springboot.NettyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPC客户端工厂类
 * @author 84215
 */
public class NettyRpcClientFactoryBean implements FactoryBean<Object>, InitializingBean,ApplicationContextAware {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private Class<?> objectType;
    private Class<?> fallback;
    private ApplicationContext applicationContext;
    /**
     * 是从NettyRpcClient.serviceId 字段获取的
     */
    private String serviceId;
    private ClassLoader classLoader;
    private static AtomicBoolean oncePingFlag = new AtomicBoolean(false);
    private Object instance;

    @Override
    public Object getObject() throws Exception {
        return instance;
    }

    /**
     * ping一次远程
     * @param nettyRpcClientProxy
     */
    private void oncePing(NettyRpcClientProxy nettyRpcClientProxy){
        if(oncePingFlag != null && oncePingFlag.compareAndSet(false,true)){
            ThreadPoolX.getDefaultInstance().execute(()->{

                    try {
                        nettyRpcClientProxy.pingOnceAfterDestroy();
                    }catch (RpcException e){
                        logger.error("无法连接至远程地址 " + e.toString());
                    }finally {
                        oncePingFlag = null;
                    }

            });
        }
    }

    @Override
    public Class<?> getObjectType() {
        return objectType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.isTrue(serviceId!= null && serviceId.length() > 0,"服务ID不能为空");
        NettyProperties nettyConfig = applicationContext.getBean(NettyProperties.class);
        NettyRpcLoadBalanced loadBalanced = applicationContext.getBean(NettyRpcLoadBalanced.class);

        NettyRpcClientProxy nettyRpcClientProxy = new NettyRpcClientProxy(serviceId,null,objectType,nettyConfig,loadBalanced);
        instance = Proxy.newProxyInstance(classLoader,new Class[]{objectType},nettyRpcClientProxy);

        oncePing(nettyRpcClientProxy);
    }

    public Class<?> getFallback() {
        return fallback;
    }

    public void setFallback(Class<?> fallback) {
        this.fallback = fallback;
    }

    public void setObjectType(Class<?> objectType) {
        this.objectType = objectType;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

}
