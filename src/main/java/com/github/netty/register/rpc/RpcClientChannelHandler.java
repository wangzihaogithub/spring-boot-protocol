package com.github.netty.register.rpc;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.constants.CoreConstants;
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
 * 客户端处理器
 * @author 84215
 */
@ChannelHandler.Sharable
public class RpcClientChannelHandler extends AbstractChannelHandler<RpcResponse> {

    /**
     * 请求锁
     */
    private Map<Long,RpcFuture> futureMap;
    /**
     * 获取链接
     */
    private Supplier<SocketChannel> channelSupplier;
    /**
     * 数据编码解码器
     */
    private DataCodec dataCodec;

    public RpcClientChannelHandler(Supplier<SocketChannel> channelSupplier) {
        this(channelSupplier, new JsonDataCodec());
    }

    public RpcClientChannelHandler(Supplier<SocketChannel> channelSupplier, DataCodec dataCodec) {
        this.channelSupplier = Objects.requireNonNull(channelSupplier);
        this.dataCodec = dataCodec;
        try {
            //用于4.0.x版本至4.1.x版本的适配
            this.futureMap = longObjectHashMapConstructor != null? (Map<Long, RpcFuture>) longObjectHashMapConstructor.newInstance(64) : new HashMap<>(64);
        } catch (Exception e) {
            this.futureMap = new HashMap<>(64);
        }
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, RpcResponse rpcResponse) throws Exception {
        if(CoreConstants.isEnableExecuteHold()) {
            CoreConstants.holdExecute(() -> {
                RpcFuture future = futureMap.remove(rpcResponse.getRequestId());
                //如果获取不到 说明已经超时, 被释放了
                if (future == null) {
                    logger.error("-----------------------!!严重"+rpcResponse);
                    return;
                }

                long out = System.currentTimeMillis() - future.beginTime;
                if(out > 10) {
                    logger.error("超时的响应[" +
                            out +
                            "] :" + rpcResponse);
                }
                future.done(rpcResponse);
            });
            return;
        }

        RpcFuture future = futureMap.remove(rpcResponse.getRequestId());
        //如果获取不到 说明已经超时, 被释放了
        if (future == null) {
            return;
        }
        future.done(rpcResponse);
    }

    /**
     * 新建实现类
     * @param timeout 超时时间
     * @param serviceName 服务名称
     * @param interfaceClass 接口类
     * @param methodToParameterNamesFunction 方法转参数名的函数
     * @return 接口的实现类
     */
    public RpcClientInstance newInstance(int timeout, String serviceName, Class interfaceClass, Function<Method,String[]> methodToParameterNamesFunction){
        RpcClientInstance rpcInstance = new RpcClientInstance(timeout, serviceName,channelSupplier,dataCodec,interfaceClass,methodToParameterNamesFunction,futureMap);
        return rpcInstance;
    }

    private static Constructor<?> longObjectHashMapConstructor;
    static {
        try {
            Class<?> longObjectHashMapClass = Class.forName("io.netty.util.collection.LongObjectHashMap");
            Constructor constructor = longObjectHashMapClass.getConstructor(int.class);
            longObjectHashMapConstructor = constructor;
        } catch (Exception e) {
            longObjectHashMapConstructor = null;
        }
    }

}