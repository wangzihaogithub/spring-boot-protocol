package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.ByteBufAllocatorX;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

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
    private Map<ByteBuf,RpcMethod> rpcMethodMap;
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

        this.rpcMethodMap = RpcMethod.toByteBufMethodMap(rpcMethodMap);
        this.instance = instance;
        this.dataCodec = dataCodec;
    }

    public RpcResponsePacket invoke(RpcRequestPacket rpcRequest){
        RpcResponsePacket rpcResponse = new RpcResponsePacket();
        rpcResponse.setRequestId(rpcRequest.getRequestId());

        RpcMethod rpcMethod = rpcMethodMap.get(rpcRequest.getMethodName());
        if(rpcMethod == null) {
            String message = "not found method " + rpcRequest.getMethodNameString();
            ByteBuf messageByteBuf = ByteBufAllocatorX.POOLED.buffer(message.length());
            messageByteBuf.writeCharSequence(message,DataCodec.CHARSET_UTF8);

            rpcResponse.setEncode(BINARY);
            rpcResponse.setStatus(NO_SUCH_METHOD);
            rpcResponse.setMessage(messageByteBuf);
            rpcResponse.setBody(null);
            return rpcResponse;
        }

        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getBody(),rpcMethod);
            Object result = rpcMethod.getMethod().invoke(instance, args);
            //Whether to code or not
            if(result instanceof byte[]){
                rpcResponse.setEncode(BINARY);
                rpcResponse.setBody(Unpooled.wrappedBuffer((byte[]) result));
            }else {
                rpcResponse.setEncode(JSON);
                rpcResponse.setBody(dataCodec.encodeResponseData(result));
            }
            rpcResponse.setStatus(OK);
            rpcResponse.setMessage(OK.getTextByteBuf());
            return rpcResponse;
        }catch (Throwable t){
            String message = t.getMessage();
            message = message == null? t.toString(): t.getMessage();
            ByteBuf messageByteBuf = ByteBufAllocatorX.POOLED.buffer(message.length());
            messageByteBuf.writeCharSequence(message, DataCodec.CHARSET_UTF8);

            rpcResponse.setEncode(BINARY);
            rpcResponse.setStatus(SERVER_ERROR);
            rpcResponse.setMessage(messageByteBuf);
            return rpcResponse;
        }
    }

    public Object getInstance() {
        return instance;
    }
}
