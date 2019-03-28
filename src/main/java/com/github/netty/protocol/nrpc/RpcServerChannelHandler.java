package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.Packet;
import com.github.netty.core.util.AsmMethodToParameterNamesFunction;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import static com.github.netty.protocol.nrpc.DataCodec.CHARSET_UTF8;
import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcResponseStatus.NO_SUCH_SERVICE;

/**
 * Server side processor
 * @author wangzihao
 *  2018/9/16/016
 */
@ChannelHandler.Sharable
public class RpcServerChannelHandler extends AbstractChannelHandler<Packet,Object> {
    /**
     * Data encoder decoder. (Serialization or Deserialization)
     */
    private DataCodec dataCodec;
    private final Map<ByteBuf,RpcServerInstance> serviceInstanceMap = new HashMap<>();

    public RpcServerChannelHandler() {
        this(new JsonDataCodec());
    }

    public RpcServerChannelHandler(DataCodec dataCodec) {
        super(true);
        this.dataCodec = dataCodec;
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if(packet instanceof RpcRequestPacket){
            onRequestReceived(ctx, (RpcRequestPacket) packet);
        }else {
            if (packet.getAck() == ACK_YES) {
                Packet responsePacket = new Packet(TYPE_PONG);
                responsePacket.setAck(ACK_NO);
                ctx.writeAndFlush(responsePacket).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }
    }

    protected void onRequestReceived(ChannelHandlerContext ctx, RpcRequestPacket request){
        RpcServerInstance rpcInstance = serviceInstanceMap.get(request.getServiceName());
        if(rpcInstance == null) {
            if(request.getAck() == ACK_YES) {
                RpcResponsePacket rpcResponse = new RpcResponsePacket();

                String message = "not found service " + request.getServiceNameString();
                ByteBuf messageByteBuf = ctx.alloc().buffer(message.length());
                messageByteBuf.writeCharSequence(message,CHARSET_UTF8);

                boolean release = true;
                try {
                    rpcResponse.setRequestId(request.getRequestId().copy());
                    rpcResponse.setEncode(BINARY);
                    rpcResponse.setStatus(NO_SUCH_SERVICE);
                    rpcResponse.setMessage(messageByteBuf);
                    ctx.writeAndFlush(rpcResponse).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                    release = false;
                }finally {
                    if(release) {
                        RecyclableUtil.release(messageByteBuf);
                        RecyclableUtil.release(rpcResponse);
                    }
                }
            }
            return;
        }

        RpcResponsePacket response = rpcInstance.invoke(request);
        if(response != null){
            try {
                response.putField(AsciiString.of("time"),
                        request.getFieldMap().get(AsciiString.of("time")).copy());
            }catch (Exception e){
                e.printStackTrace();
            }
        }
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
        addInstance(instance,getServiceName(instance.getClass()));
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
            RpcServerInstance oldServerInstance = serviceInstanceMap.put(
                    Unpooled.copiedBuffer(serviceName, CHARSET_UTF8),
                    rpcServerInstance);

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
     * Get the service name
     * @param instanceClass instanceClass
     * @return serviceName
     */
    public static String getServiceName(Class instanceClass){
        String serviceName = "";
        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(instanceClass, Protocol.RpcService.class);
        if (rpcInterfaceAnn != null) {
            serviceName = rpcInterfaceAnn.value();
        }

        if(serviceName.isEmpty()){
            Class[] classes = ReflectUtil.getInterfaces(instanceClass);
            if(classes.length > 0){
                serviceName = '/'+ StringUtil.firstLowerCase(classes[0].getSimpleName());
            }else {
                serviceName =  '/'+ StringUtil.firstLowerCase(instanceClass.getSimpleName());
            }
        }
        return serviceName;
    }

}
