package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.*;
import com.github.netty.protocol.nrpc.codec.DataCodecUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcPacket.*;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket.*;
import static com.github.netty.protocol.nrpc.RpcServerAop.CONTEXT_LOCAL;

/**
 * Server side processor
 *
 * @author wangzihao
 * 2018/9/16/016
 */
public class RpcServerChannelHandler extends AbstractChannelHandler<RpcPacket, Object> {
    private static final LoggerX logger = LoggerFactoryX.getLogger(RpcServerChannelHandler.class);

    protected final ExpiryLRUMap<RpcRunnable, RpcRunnable> rpcDoneMap = new ExpiryLRUMap<>(512, Long.MAX_VALUE, Long.MAX_VALUE, null);
    private final Map<String, RpcServerInstance> serviceInstanceMap = new ConcurrentHashMap<>(8);
    private final List<RpcServerAop> nettyRpcServerAopList = new CopyOnWriteArrayList<>();
    /**
     * Data encoder decoder. (Serialization or Deserialization)
     */
    private DataCodec dataCodec;
    private ChannelHandlerContext context;
    private Supplier<Executor> executorSupplier;
    private Executor executor;

    public RpcServerChannelHandler() {
        this(DataCodecUtil.newDataCodec());
    }

    public RpcServerChannelHandler(DataCodec dataCodec) {
        super(true);
        this.dataCodec = dataCodec;
        dataCodec.getEncodeRequestConsumerList().add(params -> {
            RpcContext<RpcServerInstance> rpcContext = CONTEXT_LOCAL.get();
            for (RpcServerAop aop : nettyRpcServerAopList) {
                aop.onDecodeRequestBefore(rpcContext, params);
            }
        });
        rpcDoneMap.setOnExpiryConsumer(node -> {
            try {
                RpcRunnable runnable = node.getData();
                if (!runnable.done) {
                    if (runnable.timeoutNotifyFlag.compareAndSet(false, true)) {
                        runnable.executor.execute(runnable::onTimeout);
                    }
                    if (runnable.timeoutInterrupt) {
                        runnable.taskThread.interrupt();
                        runnable.interruptCount++;
                        rpcDoneMap.put(runnable, runnable, 100);
                    }
                }
            } catch (Exception e) {
                logger.warn("doneTimeout exception. server = {}, message = {}.", this, e.toString(), e);
            }
        });
    }

    /**
     * Get the service name
     *
     * @param instanceClass instanceClass
     * @return requestMappingName
     */
    public static String getRequestMappingName(Class instanceClass) {
        String requestMappingName = null;
        NRpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(instanceClass, NRpcService.class);
        if (rpcInterfaceAnn != null) {
            requestMappingName = rpcInterfaceAnn.value();
        }
        return requestMappingName;
    }

    /**
     * Generate a service name
     *
     * @param instanceClass instanceClass
     * @return requestMappingName
     */
    public static String generateRequestMappingName(Class instanceClass) {
        String requestMappingName;
        Class[] classes = ReflectUtil.getInterfaces(instanceClass);
        if (classes.length > 0) {
            requestMappingName = '/' + StringUtil.firstLowerCase(classes[0].getSimpleName());
        } else {
            requestMappingName = '/' + StringUtil.firstLowerCase(instanceClass.getSimpleName());
        }
        return requestMappingName;
    }

    public static RpcContext<RpcServerInstance> newRpcContext() {
        return new RpcContext<>();
    }

    public List<RpcServerAop> getAopList() {
        return nettyRpcServerAopList;
    }

    public DataCodec getDataCodec() {
        return dataCodec;
    }

    public ChannelHandlerContext getContext() {
        return context;
    }

    public Supplier<Executor> getExecutorSupplier() {
        return executorSupplier;
    }

    public void setExecutorSupplier(Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.context = ctx;

        RpcContext<RpcServerInstance> rpcContext = newRpcContext();
        rpcContext.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
        rpcContext.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
        CONTEXT_LOCAL.set(rpcContext);
        try {
            for (RpcServerAop aop : nettyRpcServerAopList) {
                aop.onConnectAfter(this);
            }
            if (executorSupplier != null) {
                this.executor = executorSupplier.get();
            }
        } finally {
            CONTEXT_LOCAL.remove();
            super.channelActive(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RpcContext<RpcServerInstance> rpcContext = newRpcContext();
        rpcContext.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
        rpcContext.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
        CONTEXT_LOCAL.set(rpcContext);
        try {
            for (RpcServerAop aop : nettyRpcServerAopList) {
                aop.onDisconnectAfter(this);
            }
        } finally {
            CONTEXT_LOCAL.remove();
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, RpcPacket packet) throws Exception {
        final Executor threadPool = this.executor;
        boolean async = false;
        RpcContext<RpcServerInstance> rpcContext = null;
        try {
            if (packet instanceof RequestPacket) {
                RequestPacket request = (RequestPacket) packet;
                rpcContext = newRpcContext();
                try {
                    rpcContext.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
                    rpcContext.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
                    rpcContext.setRequest(request);
                    rpcContext.setRpcBeginTimestamp(System.currentTimeMillis());

                    // not found instance
                    String serverInstanceKey = RpcServerInstance.getServerInstanceKey(request.getRequestMappingName(), request.getVersion());
                    RpcServerInstance rpcInstance = serviceInstanceMap.get(serverInstanceKey);
                    if (rpcInstance == null) {
                        if (request.getAck() == ACK_YES) {
                            ResponseLastPacket response = ResponsePacket.newLastPacket();
                            rpcContext.setResponse(response);
                            boolean release = true;
                            try {
                                response.setRequestId(request.getRequestId());
                                response.setEncode(BINARY);
                                response.setStatus(ResponsePacket.NO_SUCH_SERVICE);
                                response.setMessage("not found service " + serverInstanceKey);
                                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                                release = false;
                            } finally {
                                if (release) {
                                    RecyclableUtil.release(response);
                                }
                            }
                        }
                    } else {
                        RpcMethod<RpcServerInstance> rpcMethod = rpcInstance.getRpcMethod(request.getMethodName());
                        rpcContext.setRpcMethod(rpcMethod);
                        ResponseLastPacket response = ResponsePacket.newLastPacket();
                        rpcContext.setResponse(response);
                        response.setRequestId(request.getRequestId());
                        // not found method
                        if (rpcMethod == null) {
                            response.setEncode(DataCodec.Encode.BINARY);
                            response.setStatus(NO_SUCH_METHOD);
                            response.setMessage("not found method [" + request.getMethodName() + "]");
                            response.setData(null);
                            writeAndFlush(request.getAck(), response, rpcContext, RpcContext.RpcState.WRITE_FINISH);
                        } else if (threadPool != null) {
                            // invoke method by async and call event
                            int timeout = choseTimeout(rpcInstance.getTimeout(), rpcMethod.getTimeout(), request.getTimeout());
                            rpcContext.setTimeout(timeout);
                            RpcRunnable runnable = new RpcRunnable(threadPool, rpcMethod, timeout, response, request, dataCodec, this, rpcContext);
                            if (timeout > 0) {
                                rpcDoneMap.put(runnable, runnable, timeout);
                            }
                            // execute by rpc thread pool
                            threadPool.execute(runnable);
                            async = true;
                        } else {
                            // invoke method by sync
                            CONTEXT_LOCAL.set(rpcContext);
                            Object result = null;
                            Throwable throwable = null;
                            try {
                                result = rpcInstance.invoke(rpcMethod, request, rpcContext, this);
                            } catch (Throwable t) {
                                throwable = t;
                            }
                            async = handleInvokeResult(request, response, rpcContext, this, rpcMethod, result, throwable, RpcContext.RpcState.WRITE_FINISH);
                        }
                    }
                } finally {
                    // call event
                    if (!async) {
                        rpcContext.setRpcEndTimestamp(System.currentTimeMillis());
                        CONTEXT_LOCAL.set(rpcContext);
                        onResponseAfter(rpcContext);
                    }
                }
            } else {
                // is not request, need send ack
                if (packet.getAck() == ACK_YES) {
                    RpcPacket responsePacket = new RpcPacket(RpcPacket.TYPE_PONG);
                    responsePacket.setAck(ACK_NO);
                    ctx.writeAndFlush(responsePacket).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
            }
        } finally {
            // recycle
            if (!async) {
                packet.recycle();
                if (rpcContext != null) {
                    rpcContext.recycle();
                }
            }
        }
    }

    private static boolean handleInvokeResult(RequestPacket request, ResponseLastPacket lastResponse, RpcContext<RpcServerInstance> rpcContext, RpcServerChannelHandler channelHandler, RpcMethod<RpcServerInstance> rpcMethod, Object result, Throwable throwable, State state) {
        if (result instanceof Throwable) {
            result = result.toString();
        }
        rpcContext.setResult(result);

        ResponsePacket response;
        if (throwable != null) {
            rpcContext.setThrowable(throwable);
            String message = getMessage(throwable);
            Throwable cause = getCause(throwable);
            if (cause != null && cause != throwable) {
                message = message + ". cause=" + getMessage(cause);
            }
            response = lastResponse;
            response.setEncode(DataCodec.Encode.BINARY);
            response.setData(null);
            response.setStatus(SERVER_ERROR);
            response.setMessage(message);
            logger.error("invoke error = {}", throwable.toString(), throwable);
        } else if (result instanceof Emitter) {
            Emitter<?, ?> emitter = (Emitter) result;
            emitter.setSendHandler((result1, state1) -> handleInvokeResult(request, lastResponse, rpcContext, channelHandler, rpcMethod, result1, null, state1));
            return true;
        } else if (result instanceof CompletableFuture) {
            ((CompletableFuture) result).whenComplete((result1, throwable1) -> handleInvokeResult(request, lastResponse, rpcContext, channelHandler, rpcMethod, result1, null, state));
            return true;
        } else if (result instanceof byte[]) {
            if (state == RpcContext.RpcState.WRITE_CHUNK) {
                response = ResponsePacket.newChunkPacket(lastResponse);
            } else {
                response = lastResponse;
            }
            response.setEncode(DataCodec.Encode.BINARY);
            response.setData((byte[]) result);
            response.setStatus(OK);
            response.setMessage("ok");
        } else {
            if (state == RpcContext.RpcState.WRITE_CHUNK) {
                response = ResponsePacket.newChunkPacket(lastResponse);
            } else {
                response = lastResponse;
            }
            response.setEncode(DataCodec.Encode.APP);
            response.setData(channelHandler.dataCodec.encodeResponseData(result, rpcMethod));
            response.setStatus(OK);
            response.setMessage("ok");
        }
        channelHandler.writeAndFlush(request.getAck(), response, rpcContext, state);
        return false;
    }

    private static Throwable getCause(Throwable throwable) {
        if (throwable == null || throwable.getCause() == null) {
            return null;
        }
        while (true) {
            Throwable cause = throwable;
            throwable = throwable.getCause();
            if (throwable == null) {
                return cause;
            }
        }
    }

    private static String getMessage(Throwable t) {
        String message = t.getMessage();
        return message == null ? t.toString() : message;
    }

    /**
     * timeout is -1 then never timeout
     * timeout is 0 then use client timeout
     * timeout other then use server timeout
     *
     * @param serverServiceTimeout serverServiceTimeout
     * @param serverMethodTimeout  serverMethodTimeout
     * @param clientTimeout        clientTimeout
     * @return method timeout
     */
    public int choseTimeout(Integer serverServiceTimeout, Integer serverMethodTimeout, int clientTimeout) {
        if (serverMethodTimeout != null) {
            if (serverMethodTimeout == 0) {
                return clientTimeout;
            } else {
                return serverMethodTimeout;
            }
        } else if (serverServiceTimeout != null) {
            if (serverServiceTimeout == 0) {
                return clientTimeout;
            } else {
                return serverServiceTimeout;
            }
        } else {
            return clientTimeout;
        }
    }

    private void onResponseAfter(RpcContext<RpcServerInstance> rpcContext) {
        for (RpcServerAop aop : nettyRpcServerAopList) {
            aop.onResponseAfter(rpcContext);
        }
    }

    private void writeAndFlush(int ack, ResponsePacket response, RpcContext<RpcServerInstance> rpcContext, State rpcState) {
        boolean release = true;
        try {
            if (ack == ACK_YES) {
                context.writeAndFlush(response)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                onStateUpdate(rpcContext, rpcState);
                                if (rpcState == RpcContext.RpcState.WRITE_FINISH) {
                                    onStateUpdate(rpcContext, RpcContext.RpcState.END);
                                }
                            } else {
                                future.channel().close();
                            }
                        });
                release = false;
            } else {
                onStateUpdate(rpcContext, rpcState);
                if (rpcState == RpcContext.RpcState.WRITE_FINISH) {
                    onStateUpdate(rpcContext, RpcContext.RpcState.END);
                }
            }
        } finally {
            if (release) {
                RecyclableUtil.release(response);
            }
        }
    }

    public void onStateUpdate(RpcContext<RpcServerInstance> rpcContext, State toState) {
        State formState = rpcContext.getState();
        if (formState != null && formState.isComplete()) {
            return;
        }
        rpcContext.setState(toState);
        for (RpcServerAop aop : nettyRpcServerAopList) {
            aop.onStateUpdate(rpcContext, formState, toState);
        }
    }

    /**
     * Increase the RpcServerInstance
     *
     * @param requestMappingName requestMappingName
     * @param version            rpc version
     * @param rpcServerInstance  RpcServerInstance
     */
    public void addRpcServerInstance(String requestMappingName, String version, RpcServerInstance rpcServerInstance) {
        Object instance = rpcServerInstance.getInstance();
        if (requestMappingName == null || requestMappingName.isEmpty()) {
            requestMappingName = generateRequestMappingName(instance.getClass());
        }
        String serverInstanceKey = RpcServerInstance.getServerInstanceKey(requestMappingName, version);
        if (rpcServerInstance.getDataCodec() == null) {
            rpcServerInstance.setDataCodec(dataCodec);
        }
        RpcServerInstance oldServerInstance = serviceInstanceMap.put(serverInstanceKey, rpcServerInstance);
        if (oldServerInstance != null) {
            Object oldInstance = oldServerInstance.getInstance();
            logger.warn("override instance old={}, new={}",
                    oldInstance.getClass().getSimpleName() + "@" + Integer.toHexString(oldInstance.hashCode()),
                    instance.getClass().getSimpleName() + "@" + Integer.toHexString(instance.hashCode()));
        }
        logger.trace("addInstance({}, {}, {})",
                serverInstanceKey,
                instance.getClass().getSimpleName(),
                rpcServerInstance.getMethodToParameterNamesFunction().getClass().getSimpleName());
    }

    /**
     * Increase the instance
     *
     * @param instance The implementation class
     */
    public void addInstance(Object instance) {
        addInstance(instance, getRequestMappingName(instance.getClass()), true);
    }

    /**
     * Increase the instance
     *
     * @param instance             The implementation class
     * @param requestMappingName   requestMappingName
     * @param methodOverwriteCheck methodOverwriteCheck
     */
    public void addInstance(Object instance, String requestMappingName, boolean methodOverwriteCheck) {
        String version = RpcServerInstance.getVersion(instance.getClass(), "");
        addInstance(instance, requestMappingName, version, new ClassFileMethodToParameterNamesFunction(), new AnnotationMethodToMethodNameFunction(NRpcMethod.class), methodOverwriteCheck);
    }

    /**
     * Increase the instance
     *
     * @param instance                       The implementation class
     * @param requestMappingName             requestMappingName
     * @param version                        version
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodToNameFunction           Method of extracting remote call method name
     * @param methodOverwriteCheck           methodOverwriteCheck
     */
    public void addInstance(Object instance, String requestMappingName, String version, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean methodOverwriteCheck) {
        Integer timeout = RpcServerInstance.getTimeout(instance.getClass());
        RpcServerInstance rpcServerInstance = new RpcServerInstance(instance, dataCodec, version, timeout, methodToParameterNamesFunction, methodToNameFunction, methodOverwriteCheck);
        addRpcServerInstance(requestMappingName, version, rpcServerInstance);
    }

    /**
     * Is there an instance
     *
     * @param instance instance
     * @return boolean existInstance
     */
    public boolean existInstance(Object instance) {
        if (serviceInstanceMap.isEmpty()) {
            return false;
        }
        Collection<RpcServerInstance> values = serviceInstanceMap.values();
        for (RpcServerInstance rpcServerInstance : values) {
            if (rpcServerInstance.getInstance() == instance) {
                return true;
            }
        }
        return false;
    }

    public Map<String, RpcServerInstance> getServiceInstanceMap() {
        return Collections.unmodifiableMap(serviceInstanceMap);
    }

    public static class RpcRunnable implements Runnable {
        final AtomicBoolean timeoutNotifyFlag = new AtomicBoolean();
        RpcMethod<RpcServerInstance> rpcMethod;
        RpcServerChannelHandler channelHandler;
        RequestPacket request;
        ResponseLastPacket response;
        DataCodec dataCodec;
        RpcContext<RpcServerInstance> rpcContext;
        int interruptCount = 0;
        Thread taskThread;
        boolean done = false;
        boolean timeoutInterrupt;
        int timeout;
        Executor executor;

        RpcRunnable(Executor executor, RpcMethod<RpcServerInstance> rpcMethod,
                    int timeout,
                    ResponseLastPacket response, RequestPacket request,
                    DataCodec dataCodec,
                    RpcServerChannelHandler channelHandler, RpcContext<RpcServerInstance> rpcContext) {
            this.executor = executor;
            this.rpcMethod = rpcMethod;
            this.timeout = timeout;
            this.response = response;
            this.timeoutInterrupt = rpcMethod.isTimeoutInterrupt();
            this.channelHandler = channelHandler;
            this.dataCodec = dataCodec;
            this.request = request;
            this.rpcContext = rpcContext;
        }

        public void onTimeout() {
            if (done) {
                return;
            }
            channelHandler.onStateUpdate(rpcContext, RpcContext.RpcState.TIMEOUT);
            for (RpcServerAop aop : channelHandler.nettyRpcServerAopList) {
                aop.onTimeout(rpcContext);
            }
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        @Override
        public void run() {
            taskThread = Thread.currentThread();
            CONTEXT_LOCAL.set(rpcContext);
            Object result = null;
            Throwable throwable = null;
            try {
                result = rpcMethod.getInstance().invoke(rpcMethod, request, rpcContext, channelHandler);
            } catch (Throwable t) {
                throwable = t;
            }
            done = true;
            handleInvokeResult(request, response, rpcContext, channelHandler, rpcMethod, result, throwable, RpcContext.RpcState.WRITE_FINISH);
            rpcContext.setRpcEndTimestamp(System.currentTimeMillis());
            try {
                channelHandler.onResponseAfter(rpcContext);
            } finally {
                request.recycle();
                CONTEXT_LOCAL.remove();
            }
        }
    }

}
