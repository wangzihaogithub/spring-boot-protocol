package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.AsmMethodToParameterNamesFunction;
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
 * Server side processor
 * @author wangzihao
 *  2018/9/16/016
 */
@ChannelHandler.Sharable
public class RpcServerChannelHandler extends AbstractChannelHandler<RpcRequest,Object> {

    /**
     * Data encoder decoder
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
        cause.printStackTrace();
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
     * In the connection
     * @param channel
     */
    private void putChannel(Channel channel){
        InetSocketAddress remoteAddress = getInetSocketAddress(channel.remoteAddress());
        if(remoteAddress == null){
            return;
        }
        channelMap.put(remoteAddress.getHostString() + ":" + remoteAddress.getPort(),channel);
        logger.info("New connection = "+channel.toString());
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
        logger.info("Disconnect" + channel.toString());
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     */
    public void addInstance(Object instance){
        addInstance(instance,RpcUtil.getServiceName(instance.getClass()));
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     * @param serviceName serviceName
     */
    public void addInstance(Object instance,String serviceName){
        addInstance(instance,serviceName,new AsmMethodToParameterNamesFunction());
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     * @param serviceName serviceName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     */
    public void addInstance(Object instance,String serviceName,Function<Method,String[]> methodToParameterNamesFunction){
        synchronized (serviceInstanceMap) {
            RpcServerInstance rpcServerInstance = new RpcServerInstance(instance,dataCodec,methodToParameterNamesFunction);
            RpcServerInstance oldServerInstance = serviceInstanceMap.put(serviceName,rpcServerInstance);

            if (oldServerInstance != null) {
                Object oldInstance = oldServerInstance.getInstance();
                logger.warn("override instance old={}, new={}",
                        oldInstance.getClass().getSimpleName() +"@"+ Integer.toHexString(oldInstance.hashCode()),
                        instance.getClass().getSimpleName() +"@"+  Integer.toHexString(instance.hashCode()));
            }
        }

        logger.info("addInstance({}, {}, {})",
                serviceName,
                instance.getClass().getSimpleName(),
                methodToParameterNamesFunction.getClass().getSimpleName());
    }

    /**
     * Is there an instance
     * @param instance instance
     * @return boolean existInstance
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
     * Gets the web address of the server
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
