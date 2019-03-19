package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.AsmMethodToParameterNamesFunction;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcPacket.*;
import static com.github.netty.protocol.nrpc.RpcUtil.NO_SUCH_SERVICE;

/**
 * Server side processor
 * @author wangzihao
 *  2018/9/16/016
 */
@ChannelHandler.Sharable
public class RpcServerChannelHandler extends AbstractChannelHandler<RpcPacket,Object> {
    /**
     * Data encoder decoder
     */
    private DataCodec dataCodec;
    private final Map<String,RpcServerInstance> serviceInstanceMap = new HashMap<>();

    public RpcServerChannelHandler() {
        this(new JsonDataCodec());
    }

    public RpcServerChannelHandler(DataCodec dataCodec) {
        super(false);
        this.dataCodec = dataCodec;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, RpcPacket rpcPacket) throws Exception {
        int packetType = rpcPacket.getPacketType();
        switch (packetType){
            case REQUEST_TYPE:{
                RequestPacket request = (RequestPacket) rpcPacket;
                ResponsePacket rpcResponse;

                RpcServerInstance rpcInstance = serviceInstanceMap.get(request.getServiceName());
                if(rpcInstance == null){
                    rpcResponse = new ResponsePacket(request.getRequestId());
                    rpcResponse.setEncode(DataCodec.Encode.BINARY);
                    rpcResponse.setStatus(NO_SUCH_SERVICE);
                    rpcResponse.setMessage("not found service ["+request.getServiceName()+"]");
                    rpcResponse.setData(null);
                }else {
                    rpcResponse = rpcInstance.invoke(request);
                }

                if(request.getAck() == ACK_YES) {
                    ctx.writeAndFlush(rpcResponse).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
                break;
            }
            default:{
                if(rpcPacket.getAck() == ACK_YES){
                    RpcPacket packet = new RpcPacket(PONG_TYPE);
                    packet.setAck(ACK_NO);
                    ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
            }
        }
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

}
