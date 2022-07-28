package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.core.util.ClassFileMethodToParameterNamesFunction;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.ReflectUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rpc Method
 *
 * @author wangzihao
 */
public class RpcMethod<INSTANCE> {
    private static final LoggerX LOGGERX = LoggerFactoryX.getLogger(RpcMethod.class);
    private static final Class<?> JDK9_PUBLISHER_CLASS;
    private static final Class<?> REACTIVE_PUBLISHER_CLASS;
    private static final Class<?> RXJAVA3_OBSERVABLE_CLASS;
    private static final Class<?> RXJAVA3_FLOWABLE_CLASS;

    static {
        JDK9_PUBLISHER_CLASS = classForName("java.util.concurrent.Flow.Publisher");
        REACTIVE_PUBLISHER_CLASS = classForName("org.reactivestreams.Publisher");
        RXJAVA3_OBSERVABLE_CLASS = classForName("io.reactivex.rxjava3.core.Observable");
        RXJAVA3_FLOWABLE_CLASS = classForName("io.reactivex.rxjava3.core.Flowable");
    }

    private final String methodName;
    private final Method method;
    private final Class<?>[] parameterTypes;
    private final String[] parameterNames;
    private final Type genericReturnType;
    private final Type chunkGenericReturnType;
    private final INSTANCE instance;
    private final NRpcMethod methodAnnotation;
    private final boolean returnChunkCompletionFlag;
    private final boolean returnCompletionStageFlag;
    private final boolean returnFutureFlag;
    private final boolean returnRxjava3FlowableFlag;
    private final boolean returnRxjava3ObservableFlag;
    private final boolean returnTypeJdk9PublisherFlag;
    private final boolean returnTypeReactivePublisherFlag;
    private final boolean innerMethodFlag;
    private final String methodDescriptorName;
    private final String parameterTypeDescriptorName;
    private final MethodHandle methodHandle;
    private final int parameterCount;
    private String loggerName;

    private RpcMethod(INSTANCE instance, Method method, String[] parameterNames, String methodName,
                      NRpcMethod methodAnnotation,
                      boolean returnTypeJdk9PublisherFlag, boolean returnTypeReactivePublisherFlag,
                      boolean returnRxjava3ObservableFlag, boolean returnRxjava3FlowableFlag) {
        this.instance = instance;
        this.method = method;
        this.methodName = methodName;
        this.parameterNames = parameterNames;
        this.methodAnnotation = methodAnnotation;
        this.returnTypeJdk9PublisherFlag = returnTypeJdk9PublisherFlag;
        this.returnTypeReactivePublisherFlag = returnTypeReactivePublisherFlag;
        this.returnRxjava3ObservableFlag = returnRxjava3ObservableFlag;
        this.returnRxjava3FlowableFlag = returnRxjava3FlowableFlag;
        this.returnChunkCompletionFlag = RpcClientChunkCompletableFuture.class.isAssignableFrom(method.getReturnType());
        this.returnCompletionStageFlag = CompletionStage.class.isAssignableFrom(method.getReturnType());
        this.returnFutureFlag = Future.class.isAssignableFrom(method.getReturnType());
        this.parameterTypes = method.getParameterTypes();
        if (isReturnAsync()) {
            this.genericReturnType = getParameterizedType(method, 0);
        } else {
            this.genericReturnType = method.getGenericReturnType();
        }
        if (returnChunkCompletionFlag) {
            this.chunkGenericReturnType = getParameterizedType(method, 1);
        } else {
            this.chunkGenericReturnType = null;
        }
        this.loggerName = method.getDeclaringClass().getName() + "-" + method.getName();
        this.innerMethodFlag = RpcServerInstance.isRpcInnerClass(method.getDeclaringClass());
        this.parameterTypeDescriptorName = Stream.of(parameterTypes)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        this.methodDescriptorName = getMethodDescriptorName(method);
        this.parameterCount = method.getParameterCount();
        MethodHandle methodHandle;
        try {
            MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
            MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            methodHandle = publicLookup.findVirtual(method.getDeclaringClass(), method.getName(), methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            methodHandle = null;
        }
        this.methodHandle = methodHandle;
    }

    public static String getMethodDescriptorName(Method method) {
        return method.getName();
    }

    public static <INSTANCE> Map<String, RpcMethod<INSTANCE>> getMethodMap(Class source) throws UnsupportedOperationException {
        return getMethodMap(null, source, null, Method::getName, false);
    }

    public static <INSTANCE> Map<String, RpcMethod<INSTANCE>> getMethodMap(INSTANCE instance, Class source, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean overwriteCheck) throws UnsupportedOperationException {
        Map<String, RpcMethod<INSTANCE>> methodMap = new HashMap<>(6);
        if (methodToParameterNamesFunction != null && methodToNameFunction != null) {
            Class[] interfaceClasses = ReflectUtil.getInterfaces(source);
            for (Class interfaceClass : interfaceClasses) {
                initMethodsMap(instance, interfaceClass, methodMap, methodToParameterNamesFunction, methodToNameFunction, overwriteCheck);
            }
        }
        if (!source.isInterface()) {
            initMethodsMap(instance, source, methodMap, new ClassFileMethodToParameterNamesFunction(), methodToNameFunction, overwriteCheck);
        }
        return methodMap;
    }

    private static <INSTANCE> void initMethodsMap(INSTANCE instance, Class source, Map<String, RpcMethod<INSTANCE>> methodMap, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean overwriteCheck) throws UnsupportedOperationException {
        Method[] methods = source.isInterface() ? source.getDeclaredMethods() : source.getMethods();
        for (Method method : methods) {
            Class<?> declaringClass = method.getDeclaringClass();
            if (declaringClass == Object.class) {
                continue;
            }
            String[] parameterNames;
            try {
                parameterNames = methodToParameterNamesFunction.apply(method);
            } catch (IllegalStateException e) {
                LOGGERX.warn("skip init method. source={}, method={}, cause={}", source.getSimpleName(), method, e.toString());
                continue;
            }
            if (method.getParameterCount() != parameterNames.length) {
                continue;
            }

            String methodName = methodToNameFunction.apply(method);
            boolean isReturnTypeJdk9Publisher = isReturnType(JDK9_PUBLISHER_CLASS, method);
            boolean isReturnTypeReactivePublisher = isReturnType(REACTIVE_PUBLISHER_CLASS, method);
            boolean isReturnRxjava3ObservableFlag = isReturnType(RXJAVA3_OBSERVABLE_CLASS, method);
            boolean isReturnRxjava3FlowableFlag = isReturnType(RXJAVA3_FLOWABLE_CLASS, method);
            NRpcMethod methodAnnotation = method.getDeclaredAnnotation(NRpcMethod.class);
            RpcMethod<INSTANCE> newMethod = new RpcMethod<>(instance, method, parameterNames, methodName,
                    methodAnnotation, isReturnTypeJdk9Publisher, isReturnTypeReactivePublisher,
                    isReturnRxjava3ObservableFlag, isReturnRxjava3FlowableFlag);
            RpcMethod<INSTANCE> oldMethod = methodMap.put(newMethod.getMethodDescriptorName(), newMethod);
            boolean existOverwrite = oldMethod != null;
            if (existOverwrite && overwriteCheck && !Objects.equals(oldMethod, newMethod)) {
                String message = "Please rename method！ In the non-rigorous public method calls, public method name needs to be unique. You can change to any non public method." +
                        "\n" + method.getDeclaringClass().getName() + ", old=" + oldMethod + ", new=" + newMethod;
                throw new UnsupportedOperationException(message);
            }

            if (!Objects.equals(methodName, newMethod.getMethodDescriptorName())) {
                methodMap.put(methodName, newMethod);
            }
        }
    }

    private static boolean isReturnType(Class<?> type, Method method) {
        return Objects.equals(type, method.getReturnType());
    }

    private static Type getParameterizedType(Method method, int index) {
        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType) {
            return ((ParameterizedType) genericReturnType).getActualTypeArguments()[index];
        }
        throw new IllegalStateException("If the method returns the type of Publisher class, you must add generics " +
                method.getDeclaringClass().getSimpleName() + "], method=[" + method.getName() + "]");
    }

    private static Class<?> classForName(String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (Throwable e) {
            clazz = null;
        }
        return clazz;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public Method getMethod() {
        return method;
    }

    public LoggerX getLog() {
        return LoggerFactoryX.getLogger(loggerName);
    }

    public MethodHandle getMethodHandle() {
        return methodHandle;
    }

    public NRpcMethod getMethodAnnotation() {
        return methodAnnotation;
    }

    public Integer getTimeout() {
        return methodAnnotation != null ? methodAnnotation.timeout() : null;
    }

    public boolean isTimeoutInterrupt() {
        return methodAnnotation != null && methodAnnotation.timeoutInterrupt();
    }

    public boolean isInnerMethodFlag() {
        return innerMethodFlag;
    }

    public boolean isReturnCompletionStageFlag() {
        return returnCompletionStageFlag;
    }

    public boolean isReturnFutureFlag() {
        return returnFutureFlag;
    }

    public boolean isReturnRxjava3FlowableFlag() {
        return returnRxjava3FlowableFlag;
    }

    public boolean isReturnRxjava3ObservableFlag() {
        return returnRxjava3ObservableFlag;
    }

    public boolean isReturnChunkCompletionFlag() {
        return returnChunkCompletionFlag;
    }

    public boolean isReturnAsync() {
        return isReturnTypeReactivePublisherFlag()
                || isReturnFutureFlag()
                || isReturnCompletionStageFlag()
                || isReturnChunkCompletionFlag()
                || isReturnRxjava3ObservableFlag()
                || isReturnRxjava3FlowableFlag()
                || isReturnTypeJdk9PublisherFlag();
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

    public Type getChunkGenericReturnType() {
        return chunkGenericReturnType;
    }

    public boolean isReturnVoid() {
        return genericReturnType == void.class || genericReturnType == Void.class;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RpcMethod)) {
            return false;
        }
        RpcMethod that = (RpcMethod) obj;
        if (this.parameterTypes.length != that.parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < this.parameterTypes.length; i++) {
            if (this.parameterTypes[i] != that.parameterTypes[i]) {
                return false;
            }
        }
        return this.parameterNames.length == that.parameterNames.length;
    }

    public String getMethodDescriptorName() {
        return methodDescriptorName;
    }

    public Class<?> getReturnType() {
        return method.getReturnType();
    }

    public String getMethodName() {
        return methodName;
    }

    public Object invoke(Object instance, Object[] args) throws Throwable {
        if (methodHandle != null) {
            Object[] methodHandleArgs = new Object[parameterCount + 1];
            methodHandleArgs[0] = instance;
            System.arraycopy(args, 0, methodHandleArgs, 1, args.length);
            return methodHandle.invokeWithArguments(methodHandleArgs);
        } else {
            return method.invoke(instance, args);
        }
    }

    public String getParameterTypeDescriptorName() {
        return parameterTypeDescriptorName;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public INSTANCE getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "RpcMethod{public " + getMethodDescriptorName() + "(" + getParameterTypeDescriptorName() + ")" + '}';
    }

}
