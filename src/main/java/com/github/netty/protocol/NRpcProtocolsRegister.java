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

/**
 * Internal RPC protocol registry
 *
 *   Request Packet (note:  1 = request type)
 *-+------8B--------+--1B--+--1B--+------2B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----2B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 *
 *
 *   Response Packet (note: 2 = response type)
 *-+------8B--------+--1B--+--1B--+------2B------+-----4B-----+---1B---+--------1B------+--length--+---1B---+-----2B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 * @author wangzihao
 * 2018/11/25/025
 */
public class NRpcProtocolsRegister extends AbstractProtocolsRegister {
    public static final int ORDER = HttpServletProtocolsRegister.ORDER + 100;

    private RpcEncoder rpcEncoder = new RpcEncoder();
    private RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
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

        pipeline.addLast(new RpcDecoder(messageMaxLength));
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
