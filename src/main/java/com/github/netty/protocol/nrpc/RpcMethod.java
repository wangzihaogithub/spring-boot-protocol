package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.ReflectUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Rpc Method
 * @author wangzihao
 */
public class RpcMethod {
    private Method method;
    private String[] parameterNames;
    private byte[] methodName;

    public RpcMethod(Method method, String[] parameterNames) {
        this.method = method;
        this.parameterNames = parameterNames;
        this.methodName = method.getName().getBytes(DataCodec.CHARSET_UTF8);
    }

    public Method getMethod() {
        return method;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public byte[] getMethodName(){
        return methodName;
    }

    public static Map<String,RpcMethod> getMethodMap(Class source, Function<Method,String[]> methodToParameterNamesFunction){
        Class[] classes = ReflectUtil.getInterfaces(source);
        Map<String,RpcMethod> methodMap = new HashMap<>(6);

        if(classes.length == 0){
            initMethod(source,methodToParameterNamesFunction,methodMap);
        }else {
            for(Class clazz : classes) {
                if(clazz != Object.class){
                    initMethod(clazz,methodToParameterNamesFunction,methodMap);
                }
            }
        }
        return methodMap;
    }

    public static Map<ByteBuf,RpcMethod> toByteBufMethodMap(Map<String,RpcMethod> methodMap){
        Map<ByteBuf,RpcMethod> asciiMethodMap = new HashMap<>(methodMap.size());
        for(RpcMethod rpcMethod : methodMap.values()){
            asciiMethodMap.put(RecyclableUtil.newReadOnlyBuffer(rpcMethod.methodName),rpcMethod);
        }
        return asciiMethodMap;
    }

    private static void initMethod(Class source,Function<Method,String[]> methodToParameterNamesFunction,Map<String,RpcMethod> methodMap){
        for(Method method : source.getMethods()) {
            Class declaringClass = method.getDeclaringClass();
            //必须是自身的方法
            if(declaringClass != source || declaringClass == Object.class){
                continue;
            }

            RpcMethod rpcMethod = new RpcMethod(method,methodToParameterNamesFunction.apply(method));
            RpcMethod oldMethod = methodMap.put(rpcMethod.getMethod().getName(),rpcMethod);
            if(oldMethod != null){
                throw new IllegalStateException("Exposed methods of the same class cannot have the same name, " +
                        "class=["+source.getSimpleName()+"], method=["+method.getName()+"]");
            }
        }
    }

    @Override
    public String toString() {
        return "RpcMethod{" +
                "method=" + method +
                '}';
    }
}
