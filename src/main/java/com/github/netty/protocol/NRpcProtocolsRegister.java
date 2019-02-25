package com.github.netty.protocol;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractProtocolsRegister;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.protocol.nrpc.*;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Internal RPC protocol registry
 *
 *  |8 byte fixed protocol header | body | END\r\n END character |
 * @author wangzihao
 * 2018/11/25/025
 */
public class NRpcProtocolsRegister extends AbstractProtocolsRegister {
    public static final int ORDER = HttpServletProtocolsRegister.ORDER + 100;

    private RpcEncoder rpcEncoder = new RpcEncoder();
    private RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
    private Supplier rpcRequestSupplier = RpcRequest::new;
    private ApplicationX application;
    private AtomicBoolean addInstancePluginsFlag = new AtomicBoolean(false);
    /**
     * Maximum message length per pass
     */
    private int messageMaxLength;

    public NRpcProtocolsRegister(int messageMaxLength,ApplicationX application) {
        this.messageMaxLength = messageMaxLength;
        this.application = application;
    }

    public void addInstance(Object instance){
        rpcServerHandler.addInstance(instance);
    }

    public void addInstance(Object instance,String serviceName,Function<Method,String[]> methodToParameterNamesFunction){
        rpcServerHandler.addInstance(instance,serviceName,methodToParameterNamesFunction);
    }

    public boolean existInstance(Object instance){
        return rpcServerHandler.existInstance(instance);
    }

    @Override
    public String getProtocolName() {
        return "nrpc";
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        return RpcUtil.isRpcProtocols(msg);
    }

    @Override
    public void registerTo(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        pipeline.addLast(new RpcDecoder(messageMaxLength,rpcRequestSupplier));
        pipeline.addLast(rpcEncoder);
        pipeline.addLast(rpcServerHandler);
    }

    @Override
    public int order() {
        return ORDER;
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

}
