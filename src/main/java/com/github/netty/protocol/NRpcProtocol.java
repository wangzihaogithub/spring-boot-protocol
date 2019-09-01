package com.github.netty.protocol;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.protocol.nrpc.RpcDecoder;
import com.github.netty.protocol.nrpc.RpcEncoder;
import com.github.netty.protocol.nrpc.RpcServerChannelHandler;
import com.github.netty.protocol.nrpc.RpcVersion;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Internal RPC protocol registry
 *
 *  ACK flag : (0=Don't need, 1=Need)
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
    private RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
    private ApplicationX application;
    private AtomicBoolean addInstancePluginsFlag = new AtomicBoolean(false);
    /**
     * Maximum message length per pass
     */
    private int messageMaxLength = 10 * 1024 * 1024;

    public NRpcProtocol(ApplicationX application) {
        this.application = application;
    }

    public void addInstance(Object instance){
        rpcServerHandler.addInstance(instance);
    }

    public void addInstance(Object instance,String requestMappingName,Function<Method,String[]> methodToParameterNamesFunction){
        rpcServerHandler.addInstance(instance,requestMappingName,methodToParameterNamesFunction);
    }

    public boolean existInstance(Object instance){
        return rpcServerHandler.existInstance(instance);
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
}
