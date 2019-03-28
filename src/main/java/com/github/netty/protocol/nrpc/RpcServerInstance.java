package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.ByteBufAllocatorX;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.core.Packet.ACK_NO;
import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.DataCodec.Encode.JSON;
import static com.github.netty.protocol.nrpc.RpcResponseStatus.*;

/**
 * RPC server instance
 * @author wangzihao
 */
public class RpcServerInstance {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(RpcServerInstance.class);

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
        RpcMethod rpcMethod = rpcMethodMap.get(rpcRequest.getMethodName());

        if (rpcMethod == null) {
            if(rpcRequest.getAck() == ACK_NO) {
                return null;
            }

            boolean release = true;
            RpcResponsePacket rpcResponse = new RpcResponsePacket();
            try {
                String message = "not found method " + rpcRequest.getMethodNameString();
                ByteBuf messageByteBuf = ByteBufAllocatorX.POOLED.buffer(message.length());
                messageByteBuf.writeCharSequence(message, DataCodec.CHARSET_UTF8);

                rpcResponse.setRequestId(rpcRequest.getRequestId().copy());
                rpcResponse.setEncode(BINARY);
                rpcResponse.setStatus(NO_SUCH_METHOD);
                rpcResponse.setMessage(messageByteBuf);
                release = false;
                return rpcResponse;
            }finally {
                if(release) {
                    RecyclableUtil.release(rpcResponse);
                }
            }
        }


        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getBody(),rpcMethod);
            Object result = rpcMethod.getMethod().invoke(instance, args);

            RpcResponsePacket rpcResponse = new RpcResponsePacket();
            boolean release = true;
            try {
                //Whether to code or not
                if(result instanceof byte[]){
                    rpcResponse.setEncode(BINARY);
                    rpcResponse.setBody(RecyclableUtil.newReadOnlyBuffer((byte[]) result));
                }else {
                    rpcResponse.setEncode(JSON);
                    rpcResponse.setBody(dataCodec.encodeResponseData(result));
                }
                rpcResponse.setRequestId(rpcRequest.getRequestId().copy());
                rpcResponse.setStatus(OK);
                rpcResponse.setMessage(OK.getTextByteBuf());
                release = false;
            }finally {
                if(release) {
                    RecyclableUtil.release(rpcResponse);
                }
            }
            return rpcResponse;

        }catch (Throwable t){
            String message = t.getMessage();
            if(message == null){
                message = t.toString();
            }
            ByteBuf messageByteBuf = ByteBufAllocatorX.POOLED.buffer(message.length());

            boolean release = true;
            RpcResponsePacket rpcResponse = new RpcResponsePacket();
            try {
                messageByteBuf.writeCharSequence(message, DataCodec.CHARSET_UTF8);

                rpcResponse.setRequestId(rpcRequest.getRequestId().copy());
                rpcResponse.setEncode(BINARY);
                rpcResponse.setStatus(SERVER_ERROR);
                rpcResponse.setMessage(messageByteBuf);
                release = false;
            }finally {
                if(release) {
                    RecyclableUtil.release(rpcResponse);
                    RecyclableUtil.release(messageByteBuf);
                }
            }
            return rpcResponse;
        }
    }

    public Object getInstance() {
        return instance;
    }
}
