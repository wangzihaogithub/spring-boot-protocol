package com.github.netty.protocol.nrpc;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * RPC server instance
 * @author wangzihao
 */
public class RpcServerInstance {
    private Object instance;
    private Map<String,RpcMethod> rpcMethodMap;
    private DataCodec dataCodec;

    /**
     * A constructor
     * @param instance The implementation class
     * @param dataCodec Data encoding and decoding
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     */
    protected RpcServerInstance(Object instance, DataCodec dataCodec, Function<Method,String[]> methodToParameterNamesFunction) {
        this.rpcMethodMap = RpcMethod.getMethodMap(instance.getClass(), methodToParameterNamesFunction);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("An RPC service must have at least one method, class=["+instance.getClass().getSimpleName()+"]");
        }
        this.instance = instance;
        this.dataCodec = dataCodec;
    }

    public RpcResponse invoke(RpcRequest rpcRequest){
        RpcResponse rpcResponse = new RpcResponse(rpcRequest.getRequestId());
        RpcMethod rpcMethod = rpcMethodMap.get(rpcRequest.getMethodName());
        if(rpcMethod == null) {
            rpcResponse.setEncode(DataCodec.Encode.BINARY);
            rpcResponse.setStatus(RpcResponse.NO_SUCH_METHOD);
            rpcResponse.setMessage("not found method [" + rpcRequest.getMethodName() + "]");
            rpcResponse.setData(null);
            return rpcResponse;
        }

        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getData(),rpcMethod);
            Object result = rpcMethod.getMethod().invoke(instance, args);
            //Whether to code or not
            if(result instanceof byte[]){
                rpcResponse.setEncode(DataCodec.Encode.BINARY);
                rpcResponse.setData((byte[]) result);
            }else {
                rpcResponse.setEncode(DataCodec.Encode.JSON);
                rpcResponse.setData(dataCodec.encodeResponseData(result));
            }
            rpcResponse.setStatus(RpcResponse.OK);
            rpcResponse.setMessage("ok");
            return rpcResponse;
        }catch (Throwable t){
            String message = t.getMessage();
            rpcResponse.setEncode(DataCodec.Encode.BINARY);
            rpcResponse.setStatus(RpcResponse.SERVER_ERROR);
            rpcResponse.setMessage(message == null? t.toString(): message);
            rpcResponse.setData(null);
            return rpcResponse;
        }
    }

    public Object getInstance() {
        return instance;
    }
}
