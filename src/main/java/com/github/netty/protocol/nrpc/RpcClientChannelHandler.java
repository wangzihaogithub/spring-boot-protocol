package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client handler
 * @author wangzihao
 */
@ChannelHandler.Sharable
public class RpcClientChannelHandler extends AbstractChannelHandler<RpcResponse,Object> {

    /**
     * Request a lock map
     */
    private Map<Integer,RpcFuture> futureMap;
    /**
     * Get connected
     */
    private Supplier<SocketChannel> channelSupplier;
    /**
     * Data encoder decoder
     */
    private DataCodec dataCodec;

    public RpcClientChannelHandler(Supplier<SocketChannel> channelSupplier) {
        this(channelSupplier, new JsonDataCodec());
    }

    public RpcClientChannelHandler(Supplier<SocketChannel> channelSupplier, DataCodec dataCodec) {
        this.channelSupplier = Objects.requireNonNull(channelSupplier);
        this.dataCodec = dataCodec;
        try {
            //For 4.0.x version to 4.1.x version adaptation
            this.futureMap = intObjectHashMapConstructor != null? (Map<Integer, RpcFuture>) intObjectHashMapConstructor.newInstance(64) : new HashMap<>(64);
        } catch (Exception e) {
            this.futureMap = new HashMap<>(64);
        }
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, RpcResponse rpcResponse) throws Exception {
        RpcFuture future = futureMap.remove(rpcResponse.getRequestId());
        //If the fetch does not indicate that the timeout has occurred, it is released
        if (future == null) {
            return;
        }
        future.done(rpcResponse);
    }

    /**
     * New implementation class
     * @param timeout timeout
     * @param serviceName serviceName
     * @param interfaceClass Interface class
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @return Interface implementation class
     */
    public RpcClientInstance newInstance(int timeout, String serviceName, Class interfaceClass, Function<Method,String[]> methodToParameterNamesFunction){
        RpcClientInstance rpcInstance = new RpcClientInstance(timeout, serviceName,channelSupplier,dataCodec,interfaceClass,methodToParameterNamesFunction,futureMap);
        return rpcInstance;
    }

    private static Constructor<?> intObjectHashMapConstructor;
    static {
        try {
            Class<?> intObjectHashMapClass = Class.forName("io.netty.util.collection.IntObjectHashMap");
            Constructor constructor = intObjectHashMapClass.getConstructor(int.class);
            intObjectHashMapConstructor = constructor;
        } catch (Exception e) {
            intObjectHashMapConstructor = null;
        }
    }

}