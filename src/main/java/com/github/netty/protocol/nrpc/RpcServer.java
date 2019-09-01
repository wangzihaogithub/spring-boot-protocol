package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

import java.net.InetSocketAddress;

/**
 * Rpc Server
 * @author wangzihao
 *  2018/8/18/018
 */
public class RpcServer extends AbstractNettyServer{
    /**
     * RPC server side processor
     */
    private RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
    private RpcEncoder rpcEncoder = new RpcEncoder();

    public RpcServer(int port) {
        this("",port);
    }

    public RpcServer(String preName, int port) {
        this(preName,new InetSocketAddress(port));
    }

    public RpcServer(String preName,InetSocketAddress address) {
        super(preName,address);
        //The RPC basic command service is enabled by default
        addInstance(new RpcCommandServiceImpl());
        //Enabled DB service by default
        addInstance(new RpcDBServiceImpl());
    }

    /**
     * Add implementation classes (not interfaces, abstract classes)
     * @param instance instance
     */
    public void addInstance(Object instance){
        rpcServerHandler.addInstance(instance);
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     * @param requestMappingName requestMappingName
     */
    public void addInstance(Object instance,String requestMappingName){
        rpcServerHandler.addInstance(instance,requestMappingName);
    }

    /**
     * Initialize all processors
     * @return
     */
    @Override
    protected ChannelInitializer<? extends Channel> newInitializerChannelHandler() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new RpcDecoder());
                pipeline.addLast(rpcEncoder);
                pipeline.addLast(rpcServerHandler);

                //TrafficShaping
//                pipeline.addLast(new ChannelTrafficShapingHandler());
            }
        };
    }

}
