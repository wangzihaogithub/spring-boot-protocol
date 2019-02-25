package com.github.netty.springboot.client;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.ThreadPoolX;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.springboot.NettyProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPC client factory bean
 * @author wangzihao
 */
public class NettyRpcClientFactoryBean implements FactoryBean<Object>, InitializingBean,ApplicationContextAware {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private Class<?> objectType;
    private Class<?> fallback;
    private ApplicationContext applicationContext;
    /**
     * It is obtained from the NettyRpcClient.serviceId field
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
     * Ping once remote
     * @param nettyRpcClientProxy
     */
    private void pingOnce(NettyRpcClientProxy nettyRpcClientProxy){
        if(oncePingFlag != null && oncePingFlag.compareAndSet(false,true)){
            ThreadPoolX.getDefaultInstance().execute(()->{
                    try {
                        nettyRpcClientProxy.pingOnceAfterDestroy();
                    }catch (RpcException e){
                        logger.error("Unable to connect to remote address " + e.toString());
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
        Assert.isTrue(serviceId!= null && serviceId.length() > 0,"The service ID cannot be empty");
        NettyProperties nettyConfig = applicationContext.getBean(NettyProperties.class);
        NettyRpcLoadBalanced loadBalanced = applicationContext.getBean(NettyRpcLoadBalanced.class);

        NettyRpcClientProxy nettyRpcClientProxy = new NettyRpcClientProxy(serviceId,null,objectType,nettyConfig,loadBalanced);
        instance = Proxy.newProxyInstance(classLoader,new Class[]{objectType},nettyRpcClientProxy);

        pingOnce(nettyRpcClientProxy);
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
