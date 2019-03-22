package com.github.netty.protocol.nrpc;

import io.netty.util.AsciiString;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcResponseStatus.NO_SUCH_METHOD;
import static com.github.netty.protocol.nrpc.RpcResponseStatus.OK;
import static com.github.netty.protocol.nrpc.RpcResponseStatus.SERVER_ERROR;
import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.DataCodec.Encode.JSON;

/**
 * RPC server instance
 * @author wangzihao
 */
public class RpcServerInstance {
    private Object instance;
    private Map<AsciiString,RpcMethod> rpcMethodMap;
    private DataCodec dataCodec;

    /**
     * A constructor
     * @param instance The implementation class
     * @param dataCodec Data encoding and decoding
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     */
    protected RpcServerInstance(Object instance, DataCodec dataCodec, Function<Method,String[]> methodToParameterNamesFunction) {
        Map<String,RpcMethod> rpcMethodMap = RpcMethod.getMethodMap(instance.getClass(), methodToParameterNamesFunction);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("An RPC service must have at least one method, class=["+instance.getClass().getSimpleName()+"]");
        }

        this.rpcMethodMap = RpcMethod.toAsciiMethodMap(rpcMethodMap);
        this.instance = instance;
        this.dataCodec = dataCodec;
    }

    public RpcResponsePacket invoke(RpcRequestPacket rpcRequest){
        RpcResponsePacket rpcResponse = new RpcResponsePacket();
        rpcResponse.setRequestId(rpcRequest.getRequestId());

        RpcMethod rpcMethod = rpcMethodMap.get(rpcRequest.getMethodName());
        if(rpcMethod == null) {
            rpcResponse.setEncode(BINARY);
            rpcResponse.setStatus(NO_SUCH_METHOD);
            rpcResponse.setMessage(AsciiString.of("not found method [" + rpcRequest.getMethodName() + "]"));
            rpcResponse.setBody(null);
            return rpcResponse;
        }

        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getBody(),rpcMethod);
            Object result = rpcMethod.getMethod().invoke(instance, args);
            //Whether to code or not
            if(result instanceof byte[]){
                rpcResponse.setEncode(BINARY);
                rpcResponse.setBody((byte[]) result);
            }else {
                rpcResponse.setEncode(JSON);
                rpcResponse.setBody(dataCodec.encodeResponseData(result));
            }
            rpcResponse.setStatus(OK);
            rpcResponse.setMessage(OK.getTextAscii());
            return rpcResponse;
        }catch (Throwable t){
            String message = t.getMessage();
            rpcResponse.setEncode(BINARY);
            rpcResponse.setStatus(SERVER_ERROR);
            rpcResponse.setMessage(AsciiString.of(message == null? t.toString(): message));
            rpcResponse.setBody(null);
            return rpcResponse;
        }
    }

    public Object getInstance() {
        return instance;
    }
}
