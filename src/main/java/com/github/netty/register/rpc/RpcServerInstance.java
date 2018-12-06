package com.github.netty.register.rpc;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * RPC服务端实例
 * @author 84215
 */
public class RpcServerInstance {
    private Object instance;
    private Map<String,RpcMethod> rpcMethodMap;
    private DataCodec dataCodec;

    /**
     * 构造方法
     * @param instance 实现类
     * @param dataCodec 编码解码
     * @param methodToParameterNamesFunction 方法转参数名的函数
     */
    protected RpcServerInstance(Object instance, DataCodec dataCodec, Function<Method,String[]> methodToParameterNamesFunction) {
        this.rpcMethodMap = RpcMethod.getMethodMap(instance.getClass(), methodToParameterNamesFunction);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("rpc服务必须至少拥有一个方法, class=["+instance.getClass().getSimpleName()+"]");
        }
        this.instance = instance;
        this.dataCodec = dataCodec;
    }

    public RpcResponse invoke(RpcRequest rpcRequest){
        RpcResponse rpcResponse = new RpcResponse(rpcRequest.getRequestId());
        RpcMethod rpcMethod = rpcMethodMap.get(rpcRequest.getMethodName());
        if(rpcMethod == null) {
            rpcResponse.setEncode(RpcResponse.ENCODE_YES);
            rpcResponse.setStatus(RpcResponse.NO_SUCH_METHOD);
            rpcResponse.setMessage("not found method [" + rpcRequest.getMethodName() + "]");
            rpcResponse.setData(dataCodec.encodeResponseData(null));
            return rpcResponse;
        }

        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getData(),rpcMethod);
            Object result = rpcMethod.getMethod().invoke(instance, args);
            //是否进行编码
            if(result instanceof byte[]){
                rpcResponse.setEncode(RpcResponse.ENCODE_NO);
                rpcResponse.setData((byte[]) result);
            }else {
                rpcResponse.setEncode(RpcResponse.ENCODE_YES);
                rpcResponse.setData(dataCodec.encodeResponseData(result));
            }
            rpcResponse.setStatus(RpcResponse.OK);
            rpcResponse.setMessage("ok");
            return rpcResponse;
        }catch (Throwable t){
            String message = t.getMessage();
            rpcResponse.setEncode(RpcResponse.ENCODE_YES);
            rpcResponse.setStatus(RpcResponse.SERVER_ERROR);
            rpcResponse.setMessage(message == null? t.toString(): message);
            rpcResponse.setData(dataCodec.encodeResponseData(null));
            return rpcResponse;
        }
    }

    public Object getInstance() {
        return instance;
    }
}
