package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.util.AnnotationMethodToMethodNameFunction;
import com.github.netty.core.util.ClassFileMethodToParameterNamesFunction;
import com.github.netty.protocol.nrpc.codec.RpcDecoder;
import com.github.netty.protocol.nrpc.codec.RpcEncoder;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcServerChannelHandler.getRequestMappingName;

/**
 * Rpc Server
 *
 * @author wangzihao
 * 2018/8/18/018
 */
public class RpcServer extends AbstractNettyServer {
    private final Map<Object, Instance> instanceMap = new LinkedHashMap<>();
    private final AnnotationMethodToMethodNameFunction annotationMethodToMethodNameFunction = new AnnotationMethodToMethodNameFunction(NRpcMethod.class);

    /**
     * Maximum message length per pass
     */
    private int messageMaxLength = 10 * 1024 * 1024;

    public RpcServer(int port) {
        this("", port);
    }

    public RpcServer(String preName, int port) {
        this(preName, new InetSocketAddress(port));
    }

    public RpcServer(String preName, InetSocketAddress address) {
        super(preName, address);
        //The RPC basic command service is enabled by default
        addInstance(new RpcCommandServiceImpl());
        //Enabled DB service by default
        addInstance(new RpcDBServiceImpl());
    }

    public AnnotationMethodToMethodNameFunction getAnnotationMethodToMethodNameFunction() {
        return annotationMethodToMethodNameFunction;
    }

    /**
     * Add implementation classes (not interfaces, abstract classes)
     *
     * @param instance instance
     */
    public void addInstance(Object instance) {
        String version = RpcServerInstance.getVersion(instance.getClass(), "");
        Integer timeout = RpcServerInstance.getTimeout(instance.getClass());
        addInstance(instance, getRequestMappingName(instance.getClass()), version, new ClassFileMethodToParameterNamesFunction());
    }

    /**
     * Increase the instance
     *
     * @param instance                       The implementation class
     * @param requestMappingName             requestMappingName
     * @param version                        version
     * @param methodToParameterNamesFunction methodToParameterNamesFunction
     */
    public void addInstance(Object instance, String requestMappingName, String version, Function<Method, String[]> methodToParameterNamesFunction) {
        Integer timeout = RpcServerInstance.getTimeout(instance.getClass());
        instanceMap.put(instance, new Instance(instance, requestMappingName, version, timeout, methodToParameterNamesFunction));
    }

    /**
     * Increase the instance
     *
     * @param instance                       The implementation class
     * @param requestMappingName             requestMappingName
     * @param version                        version
     * @param timeout                        timeout
     * @param methodToParameterNamesFunction methodToParameterNamesFunction
     */
    public void addInstance(Object instance, String requestMappingName, String version, Integer timeout, Function<Method, String[]> methodToParameterNamesFunction) {
        instanceMap.put(instance, new Instance(instance, requestMappingName, version, timeout, methodToParameterNamesFunction));
    }

    public boolean existInstance(Object instance) {
        return instanceMap.containsKey(instance);
    }

    /**
     * Initialize all processors
     *
     * @return
     */
    @Override
    protected ChannelInitializer<? extends Channel> newWorkerChannelHandler() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
                for (Instance instance : instanceMap.values()) {
                    rpcServerHandler.addInstance(instance.instance, instance.requestMappingName, instance.version, instance.methodToParameterNamesFunction, annotationMethodToMethodNameFunction, true);
                }

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new RpcDecoder(messageMaxLength));
                pipeline.addLast(new RpcEncoder());
                pipeline.addLast(rpcServerHandler);

                //TrafficShaping
//                pipeline.addLast(new ChannelTrafficShapingHandler());
            }
        };
    }

    public int getMessageMaxLength() {
        return messageMaxLength;
    }

    public void setMessageMaxLength(int messageMaxLength) {
        this.messageMaxLength = messageMaxLength;
    }

    static class Instance {
        Object instance;
        String requestMappingName;
        String version;
        Integer timeout;
        Function<Method, String[]> methodToParameterNamesFunction;

        Instance(Object instance, String requestMappingName, String version, Integer timeout, Function<Method, String[]> methodToParameterNamesFunction) {
            this.instance = instance;
            this.requestMappingName = requestMappingName;
            this.version = version;
            this.timeout = timeout;
            this.methodToParameterNamesFunction = methodToParameterNamesFunction;
        }
    }
}
