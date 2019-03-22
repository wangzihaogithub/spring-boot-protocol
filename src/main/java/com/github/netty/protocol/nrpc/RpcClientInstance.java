package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.IOUtil;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.protocol.nrpc.exception.RpcResponseException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;
import io.netty.channel.Channel;
import io.netty.util.AsciiString;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.netty.protocol.nrpc.RpcClient.REQUEST_ID_INCR_ATTR;
import static com.github.netty.core.Packet.*;
import static com.github.netty.protocol.nrpc.RpcUtil.NO_SUCH_METHOD;

/**
 * RPC client instance
 * @author wangzihao
 */
public class RpcClientInstance implements InvocationHandler {
    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private int timeout;
    private AsciiString serviceName;
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

    protected RpcClientInstance(int timeout, String serviceName,
                                Supplier<Channel> channelSupplier,
                                DataCodec dataCodec,
                                Class interfaceClass, Function<Method,String[]> methodToParameterNamesFunction) {
        this.rpcMethodMap = RpcMethod.getMethodMap(interfaceClass,methodToParameterNamesFunction);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("The RPC service interface must have at least one method, class=["+interfaceClass.getSimpleName()+"]");
        }
        this.timeout = timeout;
        this.serviceName = AsciiString.of(serviceName);
        this.channelSupplier = channelSupplier;
        this.dataCodec = dataCodec;
    }

    /**
     * New request id
     * @return
     */
    protected AsciiString newRequestId(Channel channel){
        AtomicInteger incr = channel.attr(REQUEST_ID_INCR_ATTR).get();
        int id = incr.incrementAndGet();
        if(id < 0){
            id = 0;
            incr.set(id);
        }
        byte[] bytes = new byte[IOUtil.INT_LENGTH];
        IOUtil.setInt(bytes,0,id);
        return new AsciiString(bytes,false);
    }

    /**
     * Increase method
     * @param rpcMethod rpcMethod
     * @return boolean success
     */
    public boolean addMethod(RpcMethod rpcMethod){
        return rpcMethodMap.put(rpcMethod.getMethodName(),rpcMethod) == null;
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

        RpcFuture future = invoke(methodName,args);
        RpcResponsePacket rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
        if(rpcResponse == null){
            future.cancel();
            throw new RpcTimeoutException("RequestTimeout : maxTimeout = ["+timeout+"], ["+future+"]", true);
        }

        //All states above 400 are in error
        if(rpcResponse.getStatus().getCode() >= RpcResponseStatus.NO_SUCH_METHOD.getCode()){
            throw new RpcResponseException(rpcResponse.getStatus(),String.valueOf(rpcResponse.getMessage()),true);
        }

        //If the server is not encoded, return directly
        if(rpcResponse.getEncode() == DataCodec.Encode.BINARY) {
            return rpcResponse.getBody();
        }else {
            return dataCodec.decodeResponseData(rpcResponse.getBody());
        }
    }

    public RpcFuture invoke(String methodName,Object[] args) {
        RpcMethod rpcMethod = rpcMethodMap.get(methodName);
        if(rpcMethod == null){
            return null;
        }

        Channel channel = channelSupplier.get();

        RpcRequestPacket rpcRequest = new RpcRequestPacket();
        rpcRequest.setRequestId(newRequestId(channel));
        rpcRequest.setServiceName(serviceName);
        rpcRequest.setMethodName(AsciiString.of(methodName));
        rpcRequest.setBody(dataCodec.encodeRequestData(args,rpcMethod));
        rpcRequest.setAck(ACK_YES);
        return new RpcFuture(rpcRequest,channel);
    }

    public int getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return "RpcClientInstance{" +
                "instanceId='" + instanceId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                '}';
    }

    public static String getTimeoutApis() {
        return String.join(",", RpcFuture.TIMEOUT_API.keySet());
    }

    public static long getTotalInvokeCount() {
        return RpcFuture.TOTAL_INVOKE_COUNT.get();
    }

    public static long getTotalTimeoutCount() {
        return RpcFuture.TIMEOUT_API.values().stream().reduce(0,Integer::sum);
    }

}
