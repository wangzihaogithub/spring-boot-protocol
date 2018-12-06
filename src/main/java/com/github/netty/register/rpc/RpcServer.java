package com.github.netty.register.rpc;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.register.rpc.service.RpcCommandServiceImpl;
import com.github.netty.register.rpc.service.RpcDBServiceImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

/**
 * RPC 服务端
 * @author acer01
 *  2018/8/18/018
 */
public class RpcServer extends AbstractNettyServer{

    /**
     * rpc服务端处理器
     */
    private RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
    private Supplier rpcRequestSupplier = RpcRequest::new;
    private RpcEncoder rpcEncoder = new RpcEncoder(RpcResponse.class);

    public RpcServer(int port) {
        this("",port);
    }

    public RpcServer(String preName, int port) {
        this(preName,new InetSocketAddress(port));
    }

    public RpcServer(String preName,InetSocketAddress address) {
        super(preName,address);
        //默认开启rpc基本命令服务
        addInstance(new RpcCommandServiceImpl());
        //默认开启DB服务
        addInstance(new RpcDBServiceImpl());
    }

    /**
     * 增加实现类 (不能是接口,抽象类)
     * @param instance
     */
    public void addInstance(Object instance){
        rpcServerHandler.addInstance(instance, RpcUtil.getServiceName(instance.getClass()));
    }

    /**
     * 增加实例
     * @param instance 实现类
     * @param serviceName 服务名称
     */
    public void addInstance(Object instance,String serviceName){
        rpcServerHandler.addInstance(instance,serviceName);
    }

    /**
     * 初始化所有处理器
     * @return
     */
    @Override
    protected ChannelInitializer<? extends Channel> newInitializerChannelHandler() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new RpcDecoder(rpcRequestSupplier));
                pipeline.addLast(rpcEncoder);
                pipeline.addLast(rpcServerHandler);
            }
        };
    }

}
