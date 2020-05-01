package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.ReflectUtil;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcPacket.RequestPacket;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket.*;

/**
 * RPC server instance
 * @author wangzihao
 */
public class RpcServerInstance {
    private Object instance;
    private Map<String,RpcMethod<RpcServerInstance>> rpcMethodMap;
    private DataCodec dataCodec;
    private Function<Method,String[]> methodToParameterNamesFunction;

    /**
     * A constructor
     * @param instance The implementation class
     * @param dataCodec Data encoding and decoding
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodOverwriteCheck methodOverwriteCheck
     * @throws IllegalStateException An RPC service must have at least one method
     */
    public RpcServerInstance(Object instance, DataCodec dataCodec, Function<Method,String[]> methodToParameterNamesFunction,boolean methodOverwriteCheck) throws IllegalStateException {
        this.instance = instance;
        this.dataCodec = dataCodec;
        this.methodToParameterNamesFunction = methodToParameterNamesFunction;
        this.rpcMethodMap = RpcMethod.getMethodMap(this,instance.getClass(), methodToParameterNamesFunction,methodOverwriteCheck);
        if(rpcMethodMap.isEmpty()){
            throw new IllegalStateException("An RPC service must have at least one method, class=["+instance.getClass().getSimpleName()+"]");
        }
    }

    public Function<Method, String[]> getMethodToParameterNamesFunction() {
        return methodToParameterNamesFunction;
    }

    public static boolean isRpcInnerClass(Class clazz){
        return clazz.getPackage().getName().startsWith(RpcVersion.class.getPackage().getName());
    }

    public static String getVersion(Class clazz,String defaultReturnVersion){
        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, Protocol.RpcService.class);
        String version;
        if (rpcInterfaceAnn != null) {
            version = rpcInterfaceAnn.version();
        }else {
            version = null;
        }
        if(version == null || version.isEmpty()){
            return isRpcInnerClass(clazz)? version : defaultReturnVersion;
        }else {
            return version;
        }
    }

    public static String getServerInstanceKey(String requestMappingName,String version){
        return requestMappingName + ":" + version;
    }

    public ResponsePacket invoke(RequestPacket rpcRequest,RpcContext<RpcServerInstance> rpcContext){
        ResponsePacket rpcResponse = ResponsePacket.newInstance();
        rpcContext.setResponse(rpcResponse);
        rpcResponse.setRequestId(rpcRequest.getRequestId());
        RpcMethod<RpcServerInstance> rpcMethod = rpcMethodMap.get(rpcRequest.getMethodName());
        rpcContext.setRpcMethod(rpcMethod);
        if(rpcMethod == null) {
            rpcResponse.setEncode(DataCodec.Encode.BINARY);
            rpcResponse.setStatus(NO_SUCH_METHOD);
            rpcResponse.setMessage("not found method [" + rpcRequest.getMethodName() + "]");
            rpcResponse.setData(null);
            return rpcResponse;
        }

        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getData(),rpcMethod);
            rpcContext.setArgs(args);
            Object result = rpcMethod.invoke(instance, args);
            rpcContext.setResult(result);
            //Whether to code or not
            if(result instanceof byte[]){
                rpcResponse.setEncode(DataCodec.Encode.BINARY);
                rpcResponse.setData((byte[]) result);
            }else {
                rpcResponse.setEncode(DataCodec.Encode.JSON);
                rpcResponse.setData(dataCodec.encodeResponseData(result,rpcMethod));
            }
            rpcResponse.setStatus(OK);
            rpcResponse.setMessage("ok");
            return rpcResponse;
        }catch (Throwable t){
            rpcContext.setThrowable(t);
            String message = getMessage(t);
            Throwable cause = getCause(t);
            if(cause != null){
                message = message + ". cause=" + getMessage(cause);
            }
            rpcResponse.setEncode(DataCodec.Encode.BINARY);
            rpcResponse.setStatus(SERVER_ERROR);
            rpcResponse.setMessage(message);
            rpcResponse.setData(null);
            return rpcResponse;
        }
    }

    private Throwable getCause(Throwable throwable){
        if(throwable.getCause() == null){
            return null;
        }
        while (true){
            Throwable cause = throwable;
            throwable = throwable.getCause();
            if(throwable == null){
                return cause;
            }
        }
    }

    public DataCodec getDataCodec() {
        return dataCodec;
    }

    public void setDataCodec(DataCodec dataCodec) {
        this.dataCodec = dataCodec;
    }

    private String getMessage(Throwable t){
        String message = t.getMessage();
        return message == null? t.toString(): message;
    }

    public Object getInstance() {
        return instance;
    }
}
