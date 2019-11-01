package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.ReflectUtil;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Rpc Method
 * @author wangzihao
 */
public class RpcMethod<INSTANCE> {
    private Method method;
    private String[] parameterNames;
    private INSTANCE instance;

    public RpcMethod(INSTANCE instance,Method method, String[] parameterNames) {
        this.instance = instance;
        this.method = method;
        this.parameterNames = parameterNames;
    }

    public Method getMethod() {
        return method;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public static <INSTANCE>Map<String,RpcMethod<INSTANCE>> getMethodMap(INSTANCE instance,Class source, Function<Method,String[]> methodToParameterNamesFunction){
        Class[] classes = ReflectUtil.getInterfaces(source);
        Map<String,RpcMethod<INSTANCE>> methodMap = new HashMap<>(6);

        if(classes.length == 0){
            initMethod(instance,source,methodToParameterNamesFunction,methodMap);
        }else {
            for(Class clazz : classes) {
                if(clazz != Object.class){
                    initMethod(instance,clazz,methodToParameterNamesFunction,methodMap);
                }
            }
        }
        return methodMap;
    }

    private static <INSTANCE>void initMethod(INSTANCE instance,Class source,Function<Method,String[]> methodToParameterNamesFunction,Map<String,RpcMethod<INSTANCE>> methodMap){
        for(Method method : source.getMethods()) {
            Class declaringClass = method.getDeclaringClass();
            //必须是自身的方法
            if(declaringClass != source || declaringClass == Object.class){
                continue;
            }

            RpcMethod<INSTANCE> rpcMethod = new RpcMethod<>(instance,method,methodToParameterNamesFunction.apply(method));
            RpcMethod<INSTANCE> oldMethod = methodMap.put(rpcMethod.getMethod().getName(),rpcMethod);
            if(oldMethod != null){
                throw new IllegalStateException("Exposed methods of the same class cannot have the same name, " +
                        "class=["+source.getSimpleName()+"], method=["+method.getName()+"]");
            }
        }
    }

    public INSTANCE getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "RpcMethod{" +
                "method=" + method +
                '}';
    }
}
