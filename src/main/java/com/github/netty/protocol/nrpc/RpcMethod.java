package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.*;
import io.netty.util.concurrent.FastThreadLocal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rpc Method
 * @author wangzihao
 */
public class RpcMethod<INSTANCE> {
    private static final LoggerX LOGGERX = LoggerFactoryX.getLogger(RpcMethod.class);
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
    private final String methodDescriptorName;
    private final String parameterTypeDescriptorName;
    private final MethodHandle methodHandle;
    private FastThreadLocal<Object[]> methodHandleArgsLocal = new FastThreadLocal<Object[]>(){
        @Override
        protected Object[] initialValue() throws Exception {
            return new Object[parameterTypes.length + 1];
        }
    };
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
        this.innerMethodFlag = RpcServerInstance.isRpcInnerClass(method.getDeclaringClass());
        this.parameterTypeDescriptorName = Stream.of(parameterTypes)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        this.methodDescriptorName = getMethodDescriptorName(method);
        try {
            MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
            MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            this.methodHandle = publicLookup.findVirtual(method.getDeclaringClass(), method.getName(), methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            this.methodHandle = null;
        }
    }

    public static String getMethodDescriptorName(Method method){
        return method.getName();
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

    public boolean isReturnVoid() {
        return genericReturnType == void.class || genericReturnType == Void.class;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof RpcMethod)){
           return false;
        }
        RpcMethod that = (RpcMethod) obj;
        if(this.parameterTypes.length != that.parameterTypes.length){
            return false;
        }
        for (int i = 0; i < this.parameterTypes.length; i++) {
            if(this.parameterTypes[i] != that.parameterTypes[i]){
                return false;
            }
        }
        if(this.parameterNames.length != that.parameterNames.length){
            return false;
        }
        return true;
    }

    public String getMethodDescriptorName(){
        return methodDescriptorName;
    }

    public Class<?> getReturnType() {
        return method.getReturnType();
    }

    public String getMethodName() {
        return method.getName();
    }

    public Object invoke(Object instance,Object[] args) throws Throwable {
        if(methodHandle != null) {
            Object[] methodHandleArgs = methodHandleArgsLocal.get();
            try {
                methodHandleArgs[0] = instance;
                System.arraycopy(args, 0, methodHandleArgs, 1, args.length);
                return methodHandle.invokeWithArguments(methodHandleArgs);
            } finally {
                Arrays.fill(methodHandleArgs, null);
            }
        }else {
            return method.invoke(instance, args);
        }
    }

    public String getParameterTypeDescriptorName(){
        return parameterTypeDescriptorName;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public static <INSTANCE>Map<String,RpcMethod<INSTANCE>> getMethodMap(INSTANCE instance,Class source, Function<Method,String[]> methodToParameterNamesFunction,boolean overwriteCheck) throws UnsupportedOperationException{
        Map<String,RpcMethod<INSTANCE>> methodMap = new HashMap<>(6);
        Class[] interfaceClasses = ReflectUtil.getInterfaces(source);
        for(Class interfaceClass : interfaceClasses) {
            initMethodsMap(instance, interfaceClass, methodMap,methodToParameterNamesFunction,overwriteCheck);
        }
        if(!source.isInterface()){
            initMethodsMap(instance, source, methodMap, new ClassFileMethodToParameterNamesFunction(),overwriteCheck);
        }
        return methodMap;
    }

    private static <INSTANCE> void initMethodsMap(INSTANCE instance, Class source, Map<String, RpcMethod<INSTANCE>> methodMap, Function<Method,String[]> methodToParameterNamesFunction,boolean overwriteCheck) throws UnsupportedOperationException{
        Method[] methods = source.isInterface()? source.getDeclaredMethods() : source.getMethods();
        for(Method method : methods) {
            Class<?> declaringClass = method.getDeclaringClass();
            if(declaringClass == Object.class){
                continue;
            }
            String[] parameterNames;
            try {
                 parameterNames = methodToParameterNamesFunction.apply(method);
            }catch (IllegalStateException e){
                LOGGERX.warn("skip init method. source={}, method={}, cause={}",source.getSimpleName(),method,e.toString());
                continue;
            }
            if(method.getParameterCount() != parameterNames.length){
                continue;
            }
            boolean isReturnTypeJdk9Publisher = JDK9_PUBLISHER_CLASS != null && JDK9_PUBLISHER_CLASS.isAssignableFrom(method.getReturnType());
            boolean isReturnTypeReactivePublisher = REACTIVE_PUBLISHER_CLASS != null && REACTIVE_PUBLISHER_CLASS.isAssignableFrom(method.getReturnType());
            RpcMethod<INSTANCE> newMethod = new RpcMethod<>(instance,method,parameterNames,isReturnTypeJdk9Publisher,isReturnTypeReactivePublisher);
            RpcMethod<INSTANCE> oldMethod = methodMap.put(newMethod.getMethodDescriptorName(),newMethod);
            boolean existOverwrite = oldMethod != null;
            if(existOverwrite && overwriteCheck && !Objects.equals(oldMethod,newMethod)){
                String message = "Please rename method！ In the non-rigorous public method calls, public method name needs to be unique. You can change to any non public method." +
                        "\n"+method.getDeclaringClass().getName()+", old="+oldMethod+", new="+newMethod;
                throw new UnsupportedOperationException(message);
            }
        }
    }

    private static Type getPublisherReturnType(Method method){
        Type genericReturnType = method.getGenericReturnType();
        if(genericReturnType instanceof ParameterizedType){
            return ((ParameterizedType) genericReturnType).getActualTypeArguments()[0];
        }
        throw new IllegalStateException("If the method returns the type of Publisher class, you must add generics " +
                method.getDeclaringClass().getSimpleName()+"], method=["+method.getName()+"]");
    }

    public INSTANCE getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "RpcMethod{public " +getMethodDescriptorName()+"("+getParameterTypeDescriptorName()+")" +'}';
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
