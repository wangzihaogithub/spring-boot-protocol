package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.channel.*;
import io.netty.util.AttributeKey;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.github.netty.protocol.nrpc.RpcPacket.ACK_YES;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket;

/**
 * Client handler
 * @author wangzihao
 */
@ChannelHandler.Sharable
public class RpcClientChannelHandler extends AbstractChannelHandler<ResponsePacket,Object> {

    /**
     * Request a lock map
     */
    protected static final AttributeKey<Map<Integer,RpcFuture>> FUTURE_MAP_ATTR = AttributeKey.valueOf(Map.class+"#futureMap");
    /**
     * Channel
     */
    private Channel channel;
    /**
     * Data encoder decoder
     */
    private DataCodec dataCodec;

    public RpcClientChannelHandler(Channel channel) {
        this(channel, new JsonDataCodec());
    }

    public RpcClientChannelHandler(Channel channel, DataCodec dataCodec) {
        this.channel = Objects.requireNonNull(channel);

        this.dataCodec = dataCodec;
        try {
            //For 4.0.x version to 4.1.x version adaptation
            channel.attr(FUTURE_MAP_ATTR).set(
                    intObjectHashMapConstructor != null?
                            (Map)intObjectHashMapConstructor.newInstance(64) : new HashMap<>(64));
        } catch (Exception e) {
            channel.attr(FUTURE_MAP_ATTR).set(new HashMap<>(64));
        }
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ResponsePacket rpcResponse) throws Exception {
        Map<Integer,RpcFuture> futureMap = channel.attr(FUTURE_MAP_ATTR).get();

        RpcFuture future = futureMap.remove(rpcResponse.getRequestId());
        //If the fetch does not indicate that the timeout has occurred, it is released
        if (future == null) {
            return;
        }
        future.done(rpcResponse);
    }

    @Override
    protected void onReaderIdle(ChannelHandlerContext ctx) {
        //heart beat
        RpcPacket packet = new RpcPacket(RpcPacket.PING_TYPE);
        packet.setAck(ACK_YES);
        ctx.writeAndFlush(packet).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {

            }
        });
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