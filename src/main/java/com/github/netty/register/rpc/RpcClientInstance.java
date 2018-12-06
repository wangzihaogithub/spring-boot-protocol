package com.github.netty.register.rpc;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.register.rpc.exception.RpcResponseException;
import com.github.netty.register.rpc.exception.RpcTimeoutException;
import io.netty.channel.socket.SocketChannel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * RPC客户端实例
 * @author 84215
 */
public class RpcClientInstance implements InvocationHandler {
    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private int timeout;
    private String serviceName;
    private String instanceId = NamespaceUtil.newIdName(getClass());
    /**
     * 生成请求id
     */
    private static final AtomicLong REQUEST_ID_INCR = new AtomicLong();
    /**
     * 数据编码解码器
     */
    private DataCodec dataCodec;
    /**
     * 获取链接
     */
    private Supplier<SocketChannel> channelSupplier;
    /**
     * 请求锁
     */
    private Map<Long,RpcFuture> futureMap;

    private Map<String,RpcMethod> rpcMethodMap;

    protected RpcClientInstance(int timeout, String serviceName,
                                Supplier<SocketChannel> channelSupplier,
                                DataCodec dataCodec,
                                Class interfaceClass, Function<Method,String[]> methodToParameterNamesFunction,
                                Map<Long,RpcFuture> futureMap) {
        this.rpcMethodMap = RpcMethod.getMethodMap(interfaceClass,methodToParameterNamesFunction);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("rpc服务接口必须至少拥有一个方法, class=["+interfaceClass.getSimpleName()+"]");
        }
        this.timeout = timeout;
        this.serviceName = serviceName;
        this.channelSupplier = channelSupplier;
        this.dataCodec = dataCodec;
        this.futureMap = futureMap;
    }

    /**
     * 新建请求id
     * @return
     */
    protected long newRequestId(){
        return REQUEST_ID_INCR.incrementAndGet();
    }

    /**
     * 发送请求
     * @param rpcRequest
     */
    protected void sendRequest(RpcRequest rpcRequest){
        channelSupplier.get().writeAndFlush(rpcRequest);
    }

    /**
     * 增加方法
     * @param rpcMethod
     */
    public boolean addMethod(RpcMethod rpcMethod){
        return rpcMethodMap.put(rpcMethod.getMethodName(),rpcMethod) == null;
    }

    /**
     * 进行rpc调用
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
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
            return method.invoke(this,args);
        }

        byte[] requestDataBytes = dataCodec.encodeRequestData(args,rpcMethod);
        RpcRequest rpcRequest = new RpcRequest(newRequestId(),serviceName,methodName,requestDataBytes);
        RpcFuture future = new RpcFuture(rpcRequest);
        futureMap.put(rpcRequest.getRequestId(),future);

        sendRequest(rpcRequest);

        RpcResponse rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
        if(rpcResponse == null){
            futureMap.remove(rpcRequest.getRequestId());
            throw new RpcTimeoutException("RequestTimeout : maxTimeout = ["+timeout+"], rpcRequest = ["+rpcRequest+"]",true);
        }

        //400以上的状态都是错误状态
        if(rpcResponse.getStatus() >= RpcResponse.NO_SUCH_METHOD){
            throw new RpcResponseException(rpcResponse.getStatus(),rpcResponse.getMessage(),true);
        }

        //如果服务器未编码, 直接返回
        if(rpcResponse.getEncode() == RpcResponse.ENCODE_NO) {
            return rpcResponse.getData();
        }else {
            return dataCodec.decodeResponseData(rpcResponse.getData());
        }
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
