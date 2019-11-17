package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.ReflectUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

/**
 * Rpc Method
 * @author wangzihao
 */
public class RpcMethod<INSTANCE> {
    private static final LoggerX LOGGERX = LoggerFactoryX.getLogger(RpcMethod.class);
    private static final Collection<Class<?extends Annotation>> RPC_INTERFACE_ANNOTATION_LIST = new LinkedHashSet<>(
            Collections.singletonList(Protocol.RpcService.class));
    private static final Class<?> JDK9_PUBLISHER_CLASS;
    private static final Class<?> REACTIVE_PUBLISHER_CLASS;

    private final Method method;
    private final Class<?>[] parameterTypes;
    private final String[] parameterNames;
    private final Type genericReturnType;
    private final INSTANCE instance;
    private final boolean returnTypeJdk9PublisherFlag;
    private final boolean returnTypeReactivePublisherFlag;
    private final boolean innerMethodFlag;

    private RpcMethod(INSTANCE instance, Method method, String[] parameterNames,
                      boolean returnTypeJdk9PublisherFlag, boolean returnTypeReactivePublisherFlag) {
        this.instance = instance;
        this.method = method;
        this.parameterNames = parameterNames;
        this.returnTypeJdk9PublisherFlag = returnTypeJdk9PublisherFlag;
        this.returnTypeReactivePublisherFlag = returnTypeReactivePublisherFlag;
        this.parameterTypes = method.getParameterTypes();
        if(returnTypeJdk9PublisherFlag || returnTypeReactivePublisherFlag){
            this.genericReturnType = getPublisherReturnType(method);
        }else {
            this.genericReturnType = method.getGenericReturnType();
        }
        this.innerMethodFlag = method.getDeclaringClass().getPackage().getName().startsWith(getClass().getPackage().getName());
    }

    public static Collection<Class<? extends Annotation>> getRpcInterfaceAnnotations() {
        return RPC_INTERFACE_ANNOTATION_LIST;
    }

    public boolean isInnerMethodFlag() {
        return innerMethodFlag;
    }

    public boolean isReturnTypeJdk9PublisherFlag() {
        return returnTypeJdk9PublisherFlag;
    }

    public boolean isReturnTypeReactivePublisherFlag() {
        return returnTypeReactivePublisherFlag;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public Type getGenericReturnType() {
        return genericReturnType;
    }

    public Method getMethod() {
        return method;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public static <INSTANCE>Map<String,RpcMethod<INSTANCE>> getMethodMap(INSTANCE instance,Class source, Function<Method,String[]> methodToParameterNamesFunction){
        Map<String,RpcMethod<INSTANCE>> methodMap = new HashMap<>(6);

        Class[] interfaceClasses = ReflectUtil.getInterfaces(source);
        if(isRpcInterface(interfaceClasses)){
            for(Class interfaceClass : interfaceClasses) {
                initMethod(instance,interfaceClass,methodToParameterNamesFunction,methodMap);
            }
        }else {
            initMethod(instance,source,methodToParameterNamesFunction,methodMap);
        }
        return methodMap;
    }

    private static boolean isRpcInterface(Class[] classes){
        for (Class clazz : classes) {
            for (Class<? extends Annotation> annotation : RPC_INTERFACE_ANNOTATION_LIST) {
                if(ReflectUtil.findAnnotation(clazz, annotation) != null){
                    return true;
                }
            }
        }
        return false;
    }

    private static <INSTANCE>void initMethod(INSTANCE instance,Class source,Function<Method,String[]> methodToParameterNamesFunction,Map<String,RpcMethod<INSTANCE>> methodMap){
        for(Method method : source.getMethods()) {
            Class declaringClass = method.getDeclaringClass();
            //It must be its own method
            if(declaringClass != source || declaringClass == Object.class){
                continue;
            }
            String[] parameterNames;
            try {
                 parameterNames = methodToParameterNamesFunction.apply(method);
            }catch (IllegalStateException e){
                LOGGERX.warn("skip init method. source={}, method={}, cause={}",source.getSimpleName(),method,e.toString());
                continue;
            }

            boolean isReturnTypeJdk9Publisher = JDK9_PUBLISHER_CLASS != null && JDK9_PUBLISHER_CLASS.isAssignableFrom(method.getReturnType());
            boolean isReturnTypeReactivePublisher = REACTIVE_PUBLISHER_CLASS != null && REACTIVE_PUBLISHER_CLASS.isAssignableFrom(method.getReturnType());
            RpcMethod<INSTANCE> rpcMethod = new RpcMethod<>(instance,method,parameterNames,isReturnTypeJdk9Publisher,isReturnTypeReactivePublisher);
            RpcMethod<INSTANCE> oldMethod = methodMap.put(rpcMethod.getMethod().getName(),rpcMethod);
            if(oldMethod != null){
                throw new IllegalStateException("Exposed methods of the same class cannot have the same name, " +
                        "class=["+source.getSimpleName()+"], method=["+method.getName()+"]");
            }
        }
    }

    private static Type getPublisherReturnType(Method method){
        Type genericReturnType = method.getGenericReturnType();
        if(genericReturnType instanceof ParameterizedType){
            return ((ParameterizedType) genericReturnType).getActualTypeArguments()[0];
        }
        throw new IllegalStateException("If the method returns the type of Publisher class, you must add generics " +
                "class=["+method.getDeclaringClass().getSimpleName()+"], method=["+method.getName()+"]");
    }

    public INSTANCE getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "RpcMethod{" +method +'}';
    }

    static {
        Class<?> jdk9PublisherClass;
        try{
            jdk9PublisherClass = Class.forName("java.util.concurrent.Flow.Publisher");
        }catch (ClassNotFoundException e){
            jdk9PublisherClass = null;
        }
        JDK9_PUBLISHER_CLASS = jdk9PublisherClass;

        Class<?> publisherClass;
        try{
            publisherClass = Class.forName("org.reactivestreams.Publisher");
        }catch (ClassNotFoundException e){
            publisherClass = null;
        }
        REACTIVE_PUBLISHER_CLASS = publisherClass;
    }
}
