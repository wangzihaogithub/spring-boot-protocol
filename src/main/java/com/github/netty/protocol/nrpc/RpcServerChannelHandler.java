package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.ClassFileMethodToParameterNamesFunction;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcPacket.*;

/**
 * Server side processor
 * @author wangzihao
 *  2018/9/16/016
 */
@ChannelHandler.Sharable
public class RpcServerChannelHandler extends AbstractChannelHandler<RpcPacket,Object> {
    /**
     * Data encoder decoder. (Serialization or Deserialization)
     */
    private DataCodec dataCodec;
    private final Map<String,RpcServerInstance> serviceInstanceMap = new HashMap<>();

    public RpcServerChannelHandler() {
        this(new JsonDataCodec());
    }

    public RpcServerChannelHandler(DataCodec dataCodec) {
        super(true);
        this.dataCodec = dataCodec;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, RpcPacket packet) throws Exception {
        try {
            if (packet instanceof RequestPacket) {
                onRequestReceived(ctx, (RequestPacket) packet);
            } else {
                if (packet.getAck() == ACK_YES) {
                    RpcPacket responsePacket = new RpcPacket(RpcPacket.TYPE_PONG);
                    responsePacket.setAck(ACK_NO);
                    ctx.writeAndFlush(responsePacket).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
            }
        }finally {
            packet.recycle();
        }
    }

    protected void onRequestReceived(ChannelHandlerContext ctx, RequestPacket request){
        RpcServerInstance rpcInstance = serviceInstanceMap.get(request.getRequestMappingName());
        if(rpcInstance == null) {
            if(request.getAck() == ACK_YES) {
                ResponsePacket rpcResponse = ResponsePacket.newInstance();

                boolean release = true;
                try {
                    rpcResponse.setRequestId(request.getRequestId());
                    rpcResponse.setEncode(BINARY);
                    rpcResponse.setStatus(ResponsePacket.NO_SUCH_SERVICE);
                    rpcResponse.setMessage("not found service " + request.getRequestMappingName());
                    ctx.writeAndFlush(rpcResponse).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                    release = false;
                }finally {
                    if(release) {
                        RecyclableUtil.release(rpcResponse);
                    }
                }
            }
            return;
        }

        ResponsePacket response = rpcInstance.invoke(request);
        if(request.getAck() == ACK_YES) {
            ctx.writeAndFlush(response)
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }else {
            RecyclableUtil.release(response);
        }
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     */
    public void addInstance(Object instance){
        addInstance(instance,getRequestMappingName(instance.getClass()));
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     * @param requestMappingName requestMappingName
     */
    public void addInstance(Object instance,String requestMappingName){
        addInstance(instance,requestMappingName,new ClassFileMethodToParameterNamesFunction());
    }

    /**
     * Increase the instance
     * @param instance The implementation class
     * @param requestMappingName requestMappingName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     */
    public void addInstance(Object instance,String requestMappingName,Function<Method,String[]> methodToParameterNamesFunction){
        if(requestMappingName == null || requestMappingName.isEmpty()){
            requestMappingName = generateRequestMappingName(instance.getClass());
        }
        synchronized (serviceInstanceMap) {
            RpcServerInstance rpcServerInstance = new RpcServerInstance(instance,dataCodec,methodToParameterNamesFunction);
            RpcServerInstance oldServerInstance = serviceInstanceMap.put(requestMappingName,rpcServerInstance);

            if (oldServerInstance != null) {
                Object oldInstance = oldServerInstance.getInstance();
                logger.warn("override instance old={}, new={}",
                        oldInstance.getClass().getSimpleName() +"@"+ Integer.toHexString(oldInstance.hashCode()),
                        instance.getClass().getSimpleName() +"@"+  Integer.toHexString(instance.hashCode()));
            }
        }

        logger.info("addInstance({}, {}, {})",
                requestMappingName,
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
     * Get the service name
     * @param instanceClass instanceClass
     * @return requestMappingName
     */
    public static String getRequestMappingName(Class instanceClass){
        String requestMappingName = null;
        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(instanceClass, Protocol.RpcService.class);
        if (rpcInterfaceAnn != null) {
            requestMappingName = rpcInterfaceAnn.value();
        }
        return requestMappingName;
    }

    /**
     * Generate a service name
     * @param instanceClass instanceClass
     * @return requestMappingName
     */
    public static String generateRequestMappingName(Class instanceClass){
        String requestMappingName;
        Class[] classes = ReflectUtil.getInterfaces(instanceClass);
        if(classes.length > 0){
            requestMappingName = '/'+ StringUtil.firstLowerCase(classes[0].getSimpleName());
        }else {
            requestMappingName =  '/'+ StringUtil.firstLowerCase(instanceClass.getSimpleName());
        }
        return requestMappingName;
    }
}
