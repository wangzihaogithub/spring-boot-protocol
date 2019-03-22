package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.Packet;
import com.github.netty.core.util.AsmMethodToParameterNamesFunction;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AsciiString;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.core.Packet.*;

/**
 * Server side processor
 * @author wangzihao
 *  2018/9/16/016
 */
@ChannelHandler.Sharable
public class RpcServerChannelHandler extends AbstractChannelHandler<Packet,Object> {
    /**
     * Data encoder decoder
     */
    private DataCodec dataCodec;
    private final Map<AsciiString,RpcServerInstance> serviceInstanceMap = new HashMap<>();

    public RpcServerChannelHandler() {
        this(new JsonDataCodec());
    }

    public RpcServerChannelHandler(DataCodec dataCodec) {
        super(false);
        this.dataCodec = dataCodec;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, Packet rpcPacket) throws Exception {
        int packetType = rpcPacket.getPacketType();
        switch (packetType){
            case TYPE_REQUEST:{
                RpcRequestPacket request = (RpcRequestPacket) rpcPacket;
                RpcResponsePacket rpcResponse;

                RpcServerInstance rpcInstance = serviceInstanceMap.get(request.getServiceName());
                if(rpcInstance == null){
                    rpcResponse = new RpcResponsePacket();
                    rpcResponse.setRequestId(request.getRequestId());
                    rpcResponse.setEncode(DataCodec.Encode.BINARY);
                    rpcResponse.setStatus(RpcResponseStatus.NO_SUCH_SERVICE);
                    rpcResponse.setMessage(AsciiString.of("not found service ["+request.getServiceName()+"]"));
                    rpcResponse.setBody(null);
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
                    Packet packet = new Packet(TYPE_PONG);
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
            RpcServerInstance oldServerInstance = serviceInstanceMap.put(AsciiString.of(serviceName),rpcServerInstance);

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
