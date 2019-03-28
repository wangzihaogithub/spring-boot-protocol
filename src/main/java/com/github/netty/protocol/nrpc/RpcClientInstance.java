//package com.github.netty.protocol.nrpc;
//
//import com.github.netty.core.util.*;
//import io.netty.buffer.ByteBuf;
//import io.netty.buffer.ByteBufUtil;
//import io.netty.buffer.Unpooled;
//import io.netty.channel.ChannelFutureListener;
//import io.netty.util.AsciiString;
//
//import java.lang.reflect.InvocationHandler;
//import java.lang.reflect.Method;
//import java.util.Map;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Function;
//
//import static com.github.netty.core.Packet.ACK_YES;
//import static com.github.netty.core.util.IOUtil.INT_LENGTH;
//import static com.github.netty.protocol.nrpc.DataCodec.CHARSET_UTF8;
//import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
//
///**
// * RPC client instance
// * @author wangzihao
// */
//public class RpcClientInstance {
////    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
////    private int timeout;
////    private byte[] serviceName;
////    private String instanceId = NamespaceUtil.newIdName(getClass());
////    /**
////     * channelSupplier
////     */
////    private RpcClient rpcClient;
////    private Map<String,RpcMethod> rpcMethodMap;
////    private byte[] requestIdBytes  = new byte[INT_LENGTH];
////
////    protected RpcClientInstance(int timeout, String serviceName,
////                                RpcClient rpcClient,
////                                Class interfaceClass, Function<Method,String[]> methodToParameterNamesFunction) {
////        this.rpcMethodMap = RpcMethod.getMethodMap(interfaceClass,methodToParameterNamesFunction);
////        if(rpcMethodMap.isEmpty()){
////            throw new IllegalStateException("The RPC service interface must have at least one method, class=["+interfaceClass.getSimpleName()+"]");
////        }
////        this.timeout = timeout;
////        this.serviceName = serviceName.getBytes(CHARSET_UTF8);
////        this.rpcClient = rpcClient;
////    }
////
////    /**
////     * Increase method
////     * @param rpcMethod rpcMethod
////     * @return RpcMethod old rpcMethod
////     */
////    public RpcMethod addMethod(RpcMethod rpcMethod){
////        return rpcMethodMap.put(rpcMethod.getMethod().getName(),rpcMethod);
////    }
////
////    /**
////     * Make RPC calls
////     * @param proxy proxy
////     * @param method method
////     * @param args args
////     * @return Object
////     * @throws Throwable Throwable
////     */
////    @Override
////    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
////        String methodName = method.getName();
////        Class<?>[] parameterTypes = method.getParameterTypes();
////        if (method.getDeclaringClass() == Object.class) {
////            return method.invoke(this, args);
////        }
////        if ("toString".equals(methodName) && parameterTypes.length == 0) {
////            return this.toString();
////        }
////        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
////            return this.hashCode();
////        }
////        if ("equals".equals(methodName) && parameterTypes.length == 1) {
////            return this.equals(args[0]);
////        }
////
////        RpcMethod rpcMethod = rpcMethodMap.get(methodName);
////        if(rpcMethod == null){
////            return null;
////        }
////
////
////        int requestId = rpcClient.newRequestId();
////        IOUtil.setInt(requestIdBytes,0,requestId);
////
////        RpcRequestPacket rpcRequest = new RpcRequestPacket();
////        rpcRequest.setRequestId(RecyclableUtil.newReadOnlyBuffer(requestIdBytes));
////        rpcRequest.setServiceName(RecyclableUtil.newReadOnlyBuffer(serviceName));
////        rpcRequest.setMethodName(RecyclableUtil.newReadOnlyBuffer(rpcMethod.getMethodName()));
////        rpcRequest.setBody(rpcClient.dataCodec.encodeRequestData(args,rpcMethod));
////        rpcRequest.setAck(ACK_YES);
////        rpcRequest.getFieldMap().put(AsciiString.of("time"),Unpooled.copyLong(System.currentTimeMillis()));
////
////        RpcFuture<RpcResponsePacket> future = RpcFuture.newInstance(rpcRequest);
////        rpcClient.futureMap.put(requestId, future);
////        rpcClient.getChannel().writeAndFlush(rpcRequest).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
////
////        RpcResponsePacket rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
////        try {
////            //If the server is not encoded, return directly
////            if (rpcResponse.getEncode() == BINARY) {
////                ByteBuf body = rpcResponse.getBody();
////                return ByteBufUtil.getBytes(body, body.readerIndex(), body.readableBytes(), false);
////            } else {
////                return rpcClient.dataCodec.decodeResponseData(rpcResponse.getBody());
////            }
////        }finally {
////            RecyclableUtil.release(rpcResponse);
////        }
////    }
////
////    public int getTimeout() {
////        return timeout;
////    }
////
////    @Override
////    public String toString() {
////        return "RpcClientInstance{" +
////                "instanceId='" + instanceId + '\'' +
////                ", serviceName='" + new String(serviceName) + '\'' +
////                '}';
////    }
//
//    public static String getTimeoutApis() {
//        return String.join(",", RpcFuture.TIMEOUT_API.keySet());
//    }
//
//    public static long getTotalInvokeCount() {
//        return RpcFuture.TOTAL_INVOKE_COUNT.get();
//    }
//
//    public static long getTotalTimeoutCount() {
//        return RpcFuture.TIMEOUT_API.values().stream().reduce(0,Integer::sum);
//    }
//}
