package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.NRpcService;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.protocol.nrpc.codec.DataCodec;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcContext.RpcState.*;
import static com.github.netty.protocol.nrpc.RpcPacket.RequestPacket;

/**
 * RPC server instance
 *
 * @author wangzihao
 */
public class RpcServerInstance {
    private Object instance;
    private Map<String, RpcMethod<RpcServerInstance>> rpcMethodMap;
    private DataCodec dataCodec;
    private Function<Method, String[]> methodToParameterNamesFunction;
    private String version;
    private Integer timeout;

    /**
     * A constructor
     *
     * @param instance                       The implementation class
     * @param dataCodec                      Data encoding and decoding
     * @param timeout                        timeout
     * @param version                        version
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodToNameFunction           Method of extracting remote call method name
     * @param methodOverwriteCheck           methodOverwriteCheck
     * @throws IllegalStateException An RPC service must have at least one method
     */
    public RpcServerInstance(Object instance, DataCodec dataCodec, String version, Integer timeout, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean methodOverwriteCheck) throws IllegalStateException {
        this.instance = instance;
        this.dataCodec = dataCodec;
        this.version = version;
        this.timeout = timeout;
        this.methodToParameterNamesFunction = methodToParameterNamesFunction;
        this.rpcMethodMap = RpcMethod.getMethodMap(this, instance.getClass(), methodToParameterNamesFunction, methodToNameFunction, methodOverwriteCheck);
        if (rpcMethodMap.isEmpty()) {
            throw new IllegalStateException("An RPC service must have at least one method, class=[" + instance.getClass().getSimpleName() + "]");
        }
    }

    public static boolean isRpcInnerClass(Class clazz) {
        return clazz.getPackage().getName().startsWith(RpcVersion.class.getPackage().getName());
    }

    public static Integer getTimeout(Class clazz) {
        NRpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, NRpcService.class);
        Integer timeout;
        if (rpcInterfaceAnn != null) {
            timeout = rpcInterfaceAnn.timeout();
        } else {
            timeout = null;
        }
        return timeout;
    }

    public static String getVersion(Class clazz, String defaultReturnVersion) {
        NRpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, NRpcService.class);
        String version;
        if (rpcInterfaceAnn != null) {
            version = rpcInterfaceAnn.version();
        } else {
            version = null;
        }
        if (version == null || version.isEmpty()) {
            return isRpcInnerClass(clazz) ? version : defaultReturnVersion;
        } else {
            return version;
        }
    }

    public static String getServerInstanceKey(String requestMappingName, String version) {
        return requestMappingName + ":" + version;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, RpcMethod<RpcServerInstance>> getRpcMethodMap() {
        return rpcMethodMap;
    }

    public RpcMethod<RpcServerInstance> getRpcMethod(String methodName) {
        return rpcMethodMap.get(methodName);
    }

    public Function<Method, String[]> getMethodToParameterNamesFunction() {
        return methodToParameterNamesFunction;
    }

    public Object invoke(RpcMethod<RpcServerInstance> rpcMethod, RequestPacket rpcRequest,
                         RpcContext<RpcServerInstance> rpcContext, RpcServerChannelHandler server) throws Throwable {
        server.onStateUpdate(rpcContext, INIT);
        try {
            Object[] args = dataCodec.decodeRequestData(rpcRequest.getData(), rpcMethod);
            rpcContext.setArgs(args);
            server.onStateUpdate(rpcContext, READ_ING);

            Object result = rpcMethod.invoke(instance, args);
            server.onStateUpdate(rpcContext, READ_FINISH);
            return result;
        } finally {
            server.onStateUpdate(rpcContext, WRITE_ING);
        }
    }

    public DataCodec getDataCodec() {
        return dataCodec;
    }

    public void setDataCodec(DataCodec dataCodec) {
        this.dataCodec = dataCodec;
    }

    public Object getInstance() {
        return instance;
    }
}
