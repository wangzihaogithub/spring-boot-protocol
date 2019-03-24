package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.concurrent.FastThreadLocal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.netty.core.Packet.ACK_YES;
import static com.github.netty.core.util.IOUtil.INT_LENGTH;
import static com.github.netty.protocol.nrpc.DataCodec.CHARSET_UTF8;
import static com.github.netty.protocol.nrpc.RpcClient.REQUEST_ID_INCR_ATTR;

/**
 * RPC client instance
 * @author wangzihao
 */
public class RpcClientInstance implements InvocationHandler {
    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private int timeout;
    private byte[] serviceName;
    private String instanceId = NamespaceUtil.newIdName(getClass());
    /**
     * Data encoder decoder
     */
    private DataCodec dataCodec;
    /**
     * channelSupplier
     */
    private Supplier<Channel> channelSupplier;
    private Map<String,RpcMethod> rpcMethodMap;
    private FastThreadLocal<ByteBuf> requestIdLocal = new FastThreadLocal<ByteBuf>(){
        @Override
        protected ByteBuf initialValue() throws Exception {
            return Unpooled.wrappedBuffer(new byte[INT_LENGTH]);
        }
    };

    protected RpcClientInstance(int timeout, String serviceName,
                                Supplier<Channel> channelSupplier,
                                DataCodec dataCodec,
                                Class interfaceClass, Function<Method,String[]> methodToParameterNamesFunction) {
        this.rpcMethodMap = RpcMethod.getMethodMap(interfaceClass,methodToParameterNamesFunction);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("The RPC service interface must have at least one method, class=["+interfaceClass.getSimpleName()+"]");
        }
        this.timeout = timeout;
        this.serviceName = serviceName.getBytes(CHARSET_UTF8);
        this.channelSupplier = channelSupplier;
        this.dataCodec = dataCodec;
    }

    /**
     * New request id
     * @return
     */
    protected ByteBuf newRequestId(Channel channel){
        ByteBuf byteBuf = requestIdLocal.get();
        AtomicInteger incr = channel.attr(REQUEST_ID_INCR_ATTR).get();
        int id = incr.getAndIncrement();
        if(id < 0){
            id = 0;
            incr.set(id);
        }
        byteBuf.setIndex(0,0);
        byteBuf.writeInt(id);
        return byteBuf;
    }

    /**
     * Increase method
     * @param rpcMethod rpcMethod
     * @return RpcMethod old rpcMethod
     */
    public RpcMethod addMethod(RpcMethod rpcMethod){
        return rpcMethodMap.put(rpcMethod.getMethod().getName(),rpcMethod);
    }

    /**
     * Make RPC calls
     * @param proxy proxy
     * @param method method
     * @param args args
     * @return Object
     * @throws Throwable Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return this.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return this.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return this.equals(args[0]);
        }

        RpcMethod rpcMethod = rpcMethodMap.get(methodName);
        if(rpcMethod == null){
            return null;
        }

        Channel channel = channelSupplier.get();

        RpcRequestPacket rpcRequest = new RpcRequestPacket();
        rpcRequest.setRequestId(newRequestId(channel));
        rpcRequest.setServiceName(getServiceName());
        rpcRequest.setMethodName(rpcMethod.getMethodName());
        rpcRequest.setBody(dataCodec.encodeRequestData(args,rpcMethod));
        rpcRequest.setAck(ACK_YES);

        Future<RpcResponsePacket> future = RpcFuture.newInstance(rpcRequest, channel);
        RpcResponsePacket rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
        try {
            //If the server is not encoded, return directly
            if (rpcResponse.getEncode() == DataCodec.Encode.BINARY) {
                ByteBuf body = rpcResponse.getBody();
                return ByteBufUtil.getBytes(body, body.readerIndex(), body.readableBytes(), false);
            } else {
                return dataCodec.decodeResponseData(rpcResponse.getBody());
            }
        }finally {
            rpcResponse.release();
        }
    }

    public int getTimeout() {
        return timeout;
    }

    public ByteBuf getServiceName() {
        return RecyclableUtil.newReadOnlyBuffer(serviceName);
    }

    @Override
    public String toString() {
        return "RpcClientInstance{" +
                "instanceId='" + instanceId + '\'' +
                ", serviceName='" + new String(serviceName) + '\'' +
                '}';
    }

}
