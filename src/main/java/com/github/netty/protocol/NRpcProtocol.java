package com.github.netty.protocol;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.ClassFileMethodToParameterNamesFunction;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.nrpc.*;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcServerChannelHandler.getRequestMappingName;

/**
 * Internal RPC protocol registry
 *
 *  ACK flag : (0=Don't need, 1=Need)
 *   Request Packet (note:  1 = request type)
 *-+------8B--------+--1B--+--1B--+------4B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----4B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 *
 *
 *   Response Packet (note: 2 = response type)
 *-+------8B--------+--1B--+--1B--+------4B------+-----4B-----+---2B---+--------1B------+--length--+---1B---+-----4B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 *
 *-+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
 * |      76       |  1   |   1      |   NRPC/201 |     2       | 11requestMappingName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 * 2018/11/25/025
 */
public class NRpcProtocol extends AbstractProtocol {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private ApplicationX application;
    private AtomicBoolean addInstancePluginsFlag = new AtomicBoolean(false);
    /**
     * Maximum message length per pass
     */
    private int messageMaxLength = 10 * 1024 * 1024;
    private Map<Object,Instance> instanceMap = new HashMap<>();

    public NRpcProtocol(ApplicationX application) {
        this.application = application;
    }

    public void addInstance(Object instance){
        addInstance(instance,getRequestMappingName(instance.getClass()),new ClassFileMethodToParameterNamesFunction());
    }

    public void addInstance(Object instance,String requestMappingName,Function<Method,String[]> methodToParameterNamesFunction){
        instanceMap.put(instance,new Instance(instance,requestMappingName,methodToParameterNamesFunction));
        logger.info("addInstance({}, {}, {})",
                requestMappingName,
                instance.getClass().getSimpleName(),
                methodToParameterNamesFunction.getClass().getSimpleName());
    }

    public boolean existInstance(Object instance){
        return instanceMap.containsKey(instance);
    }

    @Override
    public String getProtocolName() {
        return RpcVersion.CURRENT_VERSION.getText();
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        return RpcVersion.CURRENT_VERSION.isSupport(msg);
    }

    @Override
    public void addPipeline(Channel channel) throws Exception {
        RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
        rpcServerHandler.getAopList().addAll(application.getBeanForType(RpcServerAop.class));
        for (Instance instance : instanceMap.values()) {
            rpcServerHandler.addInstance(instance.instance,instance.requestMappingName,instance.methodToParameterNamesFunction);
        }

        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new RpcDecoder(messageMaxLength));
        pipeline.addLast(new RpcEncoder());
        pipeline.addLast(rpcServerHandler);
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public void onServerStart() throws Exception {
        Collection list = application.getBeanForAnnotation(Protocol.RpcService.class);
        for(Object serviceImpl : list){
            if(existInstance(serviceImpl)){
                continue;
            }
            addInstance(serviceImpl);
        }
        addInstancePlugins();
        for (RpcServerAop rpcServerAop : application.getBeanForType(RpcServerAop.class)) {
            rpcServerAop.onInitAfter(this);
        }
    }

    @Override
    public void onServerStop() throws Exception {

    }

    /**
     * Add an instance of the extension
     */
    protected void addInstancePlugins(){
        if(addInstancePluginsFlag != null && addInstancePluginsFlag.compareAndSet(false,true)) {
            //The RPC basic command service is enabled by default
            addInstance(new RpcCommandServiceImpl());
            //Open DB service by default
            addInstance(new RpcDBServiceImpl());
            addInstancePluginsFlag = null;
        }
    }

    protected ApplicationX getApplication() {
        return application;
    }

    public int getMessageMaxLength() {
        return messageMaxLength;
    }

    public void setMessageMaxLength(int messageMaxLength) {
        this.messageMaxLength = messageMaxLength;
    }

    static class Instance{
        Object instance;
        String requestMappingName;
        Function<Method,String[]> methodToParameterNamesFunction;
        Instance(Object instance, String requestMappingName, Function<Method, String[]> methodToParameterNamesFunction) {
            this.instance = instance;
            this.requestMappingName = requestMappingName;
            this.methodToParameterNamesFunction = methodToParameterNamesFunction;
        }
    }
}
