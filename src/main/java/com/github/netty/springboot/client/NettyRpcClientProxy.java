package com.github.netty.springboot.client;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.AnnotationMethodToParameterNamesFunction;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcServerChannelHandler;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.springboot.NettyProperties;
import io.netty.channel.ChannelFuture;
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
import java.util.function.Supplier;

/**
 * RPC client (thread safe)
 * @author wangzihao
 */
public class NettyRpcClientProxy implements InvocationHandler {
    private static final Map<InetSocketAddress,RpcClient> CLIENT_MAP = new HashMap<>(5);

    private String serviceName;
    private String requestMappingName;
    private Class<?> interfaceClass;
    private NettyProperties properties;
    private Supplier<NettyRpcLoadBalanced> loadBalancedSupplier;
    private static final FastThreadLocal<DefaultNettyRpcRequest> REQUEST_THREAD_LOCAL = new FastThreadLocal<DefaultNettyRpcRequest>(){
        @Override
        protected DefaultNettyRpcRequest initialValue() throws Exception {
            return new DefaultNettyRpcRequest();
        }
    };
    private int timeout = 1000;

    NettyRpcClientProxy(String serviceName, String requestMappingName, Class interfaceClass, NettyProperties properties, Supplier<NettyRpcLoadBalanced> loadBalancedSupplier) {
        this.serviceName = serviceName;
        this.interfaceClass = interfaceClass;
        this.properties = properties;
        this.loadBalancedSupplier = loadBalancedSupplier;
        this.requestMappingName = StringUtil.isEmpty(requestMappingName)? getRequestMappingName(interfaceClass) : requestMappingName;
    }

    public static NettyRpcRequest getNettyRpcRequest(){
        return REQUEST_THREAD_LOCAL.get();
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

        DefaultNettyRpcRequest request = REQUEST_THREAD_LOCAL.get();
        request.args = args;
        request.method = method;
        request.clientProxy = this;

        InetSocketAddress address = chooseAddress(request);
        RpcClient rpcClient = getClient(address);
        InvocationHandler handler = rpcClient.getRpcInstance(requestMappingName);
        if(handler == null){
            List<Class<?extends Annotation>> parameterAnnotationClasses = getParameterAnnotationClasses();
            handler = rpcClient.newRpcInstance(interfaceClass, timeout,
                    requestMappingName, new AnnotationMethodToParameterNamesFunction(parameterAnnotationClasses));
        }
        return handler.invoke(proxy,method,args);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        if(timeout > 0) {
            this.timeout = timeout;
        }
    }

    protected List<Class<?extends Annotation>> getParameterAnnotationClasses(){
        return Arrays.asList(
                Protocol.RpcParam.class,RequestParam.class,RequestBody.class, RequestHeader.class,
                PathVariable.class,CookieValue.class, RequestPart.class);
    }

    protected String getRequestMappingName(Class objectType){
        String requestMappingName = RpcServerChannelHandler.getRequestMappingName(objectType);
        if(StringUtil.isNotEmpty(requestMappingName)) {
            return requestMappingName;
        }

        RequestMapping requestMapping = ReflectUtil.findAnnotation(objectType,RequestMapping.class);
        if(requestMapping != null) {
            requestMappingName = requestMapping.name();
            String[] values = requestMapping.value();
            String[] paths = requestMapping.path();
            if(StringUtil.isEmpty(requestMappingName) && values.length > 0){
                requestMappingName = values[0];
            }
            if(StringUtil.isEmpty(requestMappingName) && paths.length > 0){
                requestMappingName = paths[0];
            }
            if(StringUtil.isNotEmpty(requestMappingName)) {
                return requestMappingName;
            }
        }

        requestMappingName = RpcServerChannelHandler.generateRequestMappingName(objectType);
        return requestMappingName;
    }

    /**
     * Get the RPC client (from the current thread, if not, create it automatically)
     * @return RpcClient
     */
    private RpcClient getClient(InetSocketAddress address){
        RpcClient rpcClient = CLIENT_MAP.get(address);
        if(rpcClient == null) {
            synchronized (CLIENT_MAP){
                rpcClient = CLIENT_MAP.get(address);
                if(rpcClient == null) {
                    NettyProperties.Nrpc nrpc = properties.getNrpc();
                    rpcClient = new RpcClient(address);
                    rpcClient.setIoThreadCount(nrpc.getClientIoThreads());
                    rpcClient.setIoRatio(nrpc.getClientIoRatio());
                    rpcClient.run();
                    if (nrpc.isClientAutoReconnect()) {
                        rpcClient.enableAutoReconnect(nrpc.getClientHeartInterval(), TimeUnit.SECONDS,
                                null, nrpc.isClientEnableHeartLog());
                    }
                    CLIENT_MAP.put(address, rpcClient);
                    rpcClient.connect().ifPresent(ChannelFuture::syncUninterruptibly);
                }
            }
        }
        return rpcClient;
    }

    private InetSocketAddress chooseAddress(DefaultNettyRpcRequest request){
        InetSocketAddress address;
        try {
            address = loadBalancedSupplier.get().chooseAddress(request);
        }catch (Exception e){
            throw new RpcConnectException("Rpc Failed to select client address.  cause ["+e.getLocalizedMessage()+"]",e);
        }finally {
            request.recycle();
        }
        if (address == null) {
            throw new NullPointerException("Rpc Failed to select client address. cause [return address is null]");
        }
        return address;
    }

    /**
     * Default nett request (parameter [args array] can be modified)
     */
    private static class DefaultNettyRpcRequest implements NettyRpcRequest, Recyclable {
        private Method method;
        private Object[] args;
        private NettyRpcClientProxy clientProxy;

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public String getServiceName() {
            return clientProxy.serviceName;
        }

        @Override
        public String getRequestMappingName() {
            return clientProxy.requestMappingName;
        }

        @Override
        public NettyProperties getNettyProperties() {
            return clientProxy.properties;
        }

        @Override
        public Class getInterfaceClass() {
            return clientProxy.interfaceClass;
        }

        @Override
        public void recycle() {
            args = null;
            method = null;
            clientProxy = null;
        }
    }
}
