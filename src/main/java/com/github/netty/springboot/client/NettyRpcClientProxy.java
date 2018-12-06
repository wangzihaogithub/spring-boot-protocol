package com.github.netty.springboot.client;

import com.github.netty.annotation.RegisterFor;
import com.github.netty.core.util.AnnotationMethodToParameterNamesFunction;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import com.github.netty.register.rpc.RpcClient;
import com.github.netty.register.rpc.RpcClientInstance;
import com.github.netty.register.rpc.RpcUtil;
import com.github.netty.register.rpc.exception.RpcConnectException;
import com.github.netty.register.rpc.exception.RpcException;
import com.github.netty.springboot.NettyProperties;
import io.netty.util.concurrent.FastThreadLocal;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RPC客户端 (线程安全)
 * @author 84215
 */
public class NettyRpcClientProxy implements InvocationHandler {
    private String serviceId;
    private String serviceName;
    private Class<?> interfaceClass;
    private NettyProperties config;
    private NettyRpcLoadBalanced loadBalanced;

    private FastThreadLocal<Map<InetSocketAddress,RpcClient>> clientMapThreadLocal = new FastThreadLocal<Map<InetSocketAddress,RpcClient>>(){
        @Override
        protected Map<InetSocketAddress,RpcClient> initialValue() throws Exception {
            return new HashMap<>(5);
        }
    };
    private FastThreadLocal<DefaultNettyRpcRequest> requestThreadLocal = new FastThreadLocal<DefaultNettyRpcRequest>(){
        @Override
        protected DefaultNettyRpcRequest initialValue() throws Exception {
            return new DefaultNettyRpcRequest();
        }
    };

    NettyRpcClientProxy(String serviceId,String serviceName, Class interfaceClass, NettyProperties config, NettyRpcLoadBalanced loadBalanced) {
        this.serviceId = serviceId;
        this.interfaceClass = interfaceClass;
        this.config = config;
        this.loadBalanced = loadBalanced;
        this.serviceName = StringUtil.isEmpty(serviceName)? getServiceName(interfaceClass) : serviceName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return this.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return this.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return this.equals(args[0]);
        }

        DefaultNettyRpcRequest request = requestThreadLocal.get();
        request.setMethod(method);
        request.setArgs(args);

        InetSocketAddress address = chooseAddress(request);
        RpcClient rpcClient = getClient(address);
        RpcClientInstance rpcClientInstance = rpcClient.getRpcInstance(serviceName);
        if(rpcClientInstance == null){
            List<Class<?extends Annotation>> parameterAnnotationClasses = Arrays.asList(
                    RegisterFor.RpcParam.class,RequestParam.class,RequestBody.class, RequestHeader.class,
                    PathVariable.class,CookieValue.class, RequestPart.class);
            rpcClientInstance = rpcClient.newRpcInstance(interfaceClass,config.getRpcTimeout(),serviceName,
                    new AnnotationMethodToParameterNamesFunction(parameterAnnotationClasses));
        }
        return rpcClientInstance.invoke(proxy,method,args);
    }

    private String getServiceName(Class objectType){
        RequestMapping requestMapping = ReflectUtil.findAnnotation(objectType,RequestMapping.class);
        String serviceName = null;
        if(requestMapping != null) {
            //获取服务名
            serviceName = requestMapping.name();
            String[] values = requestMapping.value();
            String[] paths = requestMapping.path();
            if(StringUtil.isEmpty(serviceName) && values.length > 0){
                serviceName = values[0];
            }
            if(StringUtil.isEmpty(serviceName) && paths.length > 0){
                serviceName = paths[0];
            }
        }

        if(StringUtil.isEmpty(serviceName)) {
            serviceName = RpcUtil.getServiceName(objectType);
        }
        return serviceName;
    }

    /**
     * 获取RPC客户端 (从当前线程获取,如果没有则自动创建)
     * @return
     */
    private RpcClient getClient(InetSocketAddress address){
        Map<InetSocketAddress,RpcClient> rpcClientMap = clientMapThreadLocal.get();
        RpcClient rpcClient = rpcClientMap.get(address);
        if(rpcClient == null) {
            rpcClient = new RpcClient(address);
            rpcClient.setSocketChannelCount(1);
            rpcClient.setIoThreadCount(config.getRpcClientIoThreads());
            rpcClient.run();
            if (config.isEnablesRpcClientAutoReconnect()) {
                rpcClient.enableAutoReconnect(config.getRpcClientHeartIntervalSecond(), TimeUnit.SECONDS,null,config.isEnableRpcHeartLog());
            }
            rpcClientMap.put(address,rpcClient);
        }
        return rpcClient;
    }

    /**
     * ping一次 会新建客户端并销毁客户端
     * @return ping返回的消息
     * @throws RpcException
     */
    public byte[] pingOnceAfterDestroy() throws RpcException {
        InetSocketAddress address = chooseAddress(requestThreadLocal.get());
        RpcClient rpcClient = new RpcClient("Ping-",address);
        rpcClient.setSocketChannelCount(1);
        rpcClient.setIoThreadCount(1);
        rpcClient.run();

        try {
            byte[] response = rpcClient.getRpcCommandService().ping();
            return response;
        }finally {
            rpcClient.stop();
            requestThreadLocal.remove();
        }
    }

    private InetSocketAddress chooseAddress(NettyRpcRequest request){
        InetSocketAddress address;
        try {
            address = loadBalanced.chooseAddress(request);
        }catch (Exception e){
            throw new RpcConnectException("选择客户端地址失败",e);
        }
        if (address == null) {
            throw new NullPointerException("选择客户端地址失败, 获取客户端地址为null");
        }
        return address;
    }

    /**
     * 默认的nett请求 (参数[args数组] 可以修改)
     */
    private class DefaultNettyRpcRequest implements NettyRpcRequest {
        private Method method;
        private Object[] args;

        void setMethod(Method method) {
            this.method = method;
        }

        void setArgs(Object[] args) {
            this.args = args;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public String getServiceId() {
            return serviceId;
        }

        @Override
        public String getServiceName() {
            return serviceName;
        }

        @Override
        public NettyProperties getNettyProperties() {
            return config;
        }

        @Override
        public Class getInterfaceClass() {
            return interfaceClass;
        }
    }
}
