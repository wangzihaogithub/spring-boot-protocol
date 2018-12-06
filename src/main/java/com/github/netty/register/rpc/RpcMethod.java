package com.github.netty.register.rpc;

import com.github.netty.core.util.ReflectUtil;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * RPC方法
 * @author 84215
 */
public class RpcMethod {
    private Method method;
    private String[] parameterNames;

    public RpcMethod(Method method, String[] parameterNames) {
        this.method = method;
        this.parameterNames = parameterNames;
    }

    public Method getMethod() {
        return method;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public String getMethodName(){
        return method.getName();
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

    private static void initMethod(Class source,Function<Method,String[]> methodToParameterNamesFunction,Map<String,RpcMethod> methodMap){
        for(Method method : source.getMethods()) {
            Class declaringClass = method.getDeclaringClass();
            //必须是自身的方法
            if(declaringClass != source || declaringClass == Object.class){
                continue;
            }

            RpcMethod rpcMethod = new RpcMethod(method,methodToParameterNamesFunction.apply(method));
            RpcMethod oldMethod = methodMap.put(rpcMethod.getMethodName(),rpcMethod);
            if(oldMethod != null){
                throw new IllegalStateException("同一个类的暴露的方法不能重名, " +
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
