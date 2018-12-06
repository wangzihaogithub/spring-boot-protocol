package com.github.netty.register.rpc;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.AsmMethodToParameterNamesFunction;
import com.github.netty.core.util.ExceptionUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 服务端处理器
 * @author acer01
 *  2018/9/16/016
 */
@ChannelHandler.Sharable
public class RpcServerChannelHandler extends AbstractChannelHandler<RpcRequest> {

    /**
     * 数据编码解码器
     */
    private DataCodec dataCodec;
    private final Map<String,RpcServerInstance> serviceInstanceMap = new HashMap<>();
    private Map<String,Channel> channelMap = new ConcurrentHashMap<>();

    public RpcServerChannelHandler() {
        this(new JsonDataCodec());
    }

    public RpcServerChannelHandler(DataCodec dataCodec) {
        super(false);
        this.dataCodec = dataCodec;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, RpcRequest rpcRequest) throws Exception {
        RpcResponse rpcResponse;

        RpcServerInstance rpcInstance = serviceInstanceMap.get(rpcRequest.getServiceName());
        if(rpcInstance == null){
            rpcResponse = new RpcResponse(rpcRequest.getRequestId());
            rpcResponse.setEncode(RpcResponse.ENCODE_YES);
            rpcResponse.setStatus(RpcResponse.NO_SUCH_SERVICE);
            rpcResponse.setMessage("not found service ["+rpcRequest.getServiceName()+"]");
            rpcResponse.setData(dataCodec.encodeResponseData(null));
        }else {
            rpcResponse = rpcInstance.invoke(rpcRequest);
        }

        ctx.writeAndFlush(rpcResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ExceptionUtil.printRootCauseStackTrace(cause);
        removeChannel(ctx.channel());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        putChannel(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        removeChannel(ctx.channel());
    }

    /**
     * 放入链接
     * @param channel
     */
    private void putChannel(Channel channel){
        InetSocketAddress remoteAddress = getInetSocketAddress(channel.remoteAddress());
        if(remoteAddress == null){
            return;
        }
        channelMap.put(remoteAddress.getHostString() + ":" + remoteAddress.getPort(),channel);
        logger.info("新入链接 = "+channel.toString());
    }

    /**
     * 移除链接
     * @param channel
     */
    private void removeChannel(Channel channel){
        InetSocketAddress remoteAddress = getInetSocketAddress(channel.remoteAddress());
        if(remoteAddress == null){
            return;
        }
        channelMap.remove(remoteAddress.getHostString() + ":" + remoteAddress.getPort(),channel);
        logger.info("断开链接" + channel.toString());
    }

    /**
     * 增加实例
     * @param instance 实现类
     */
    public void addInstance(Object instance){
        addInstance(instance,RpcUtil.getServiceName(instance.getClass()));
    }

    /**
     * 增加实例
     * @param instance 实现类
     * @param serviceName 服务名称
     */
    public void addInstance(Object instance,String serviceName){
        addInstance(instance,serviceName,new AsmMethodToParameterNamesFunction());
    }

    /**
     * 增加实例
     * @param instance 实现类
     * @param serviceName 服务名称
     * @param methodToParameterNamesFunction 方法转参数名的函数
     */
    public void addInstance(Object instance,String serviceName,Function<Method,String[]> methodToParameterNamesFunction){
        synchronized (serviceInstanceMap) {
            RpcServerInstance rpcServerInstance = new RpcServerInstance(instance,dataCodec,methodToParameterNamesFunction);
            RpcServerInstance oldServerInstance = serviceInstanceMap.put(serviceName,rpcServerInstance);

            if (oldServerInstance != null) {
                Object oldInstance = oldServerInstance.getInstance();
                logger.warn("override instance old={0}, new={1}",
                        oldInstance.getClass().getSimpleName() +"@"+ Integer.toHexString(oldInstance.hashCode()),
                        instance.getClass().getSimpleName() +"@"+  Integer.toHexString(instance.hashCode()));
            }
        }

        logger.info("addInstance({0}, {1}, {2})",
                serviceName,
                instance.getClass().getSimpleName(),
                methodToParameterNamesFunction.getClass().getSimpleName());
    }

    /**
     * 是否存在实例
     * @param instance
     * @return
     */
    public boolean existInstance(Object instance){
        if(serviceInstanceMap.isEmpty()){
            return false;
        }
        Collection<RpcServerInstance> values = serviceInstanceMap.values();
        for(RpcServerInstance rpcServerInstance : values){
            if(rpcServerInstance.getInstance() == instance){
                return true;
            }
        }
        return false;
    }

    /**
     * 获取服务端的网络地址
     * @param socketAddress
     * @return
     */
    private InetSocketAddress getInetSocketAddress(SocketAddress socketAddress){
        if(socketAddress instanceof InetSocketAddress){
            return (InetSocketAddress) socketAddress;
        }
        return null;
    }

}
