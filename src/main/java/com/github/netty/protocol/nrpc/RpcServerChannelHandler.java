package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.*;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcPacket.*;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket.NO_SUCH_METHOD;
import static com.github.netty.protocol.nrpc.RpcServerAop.CONTEXT_LOCAL;

/**
 * Server side processor
 *
 * @author wangzihao
 * 2018/9/16/016
 */
public class RpcServerChannelHandler extends AbstractChannelHandler<RpcPacket, Object> {
    protected final ExpiryLRUMap<RpcRunnable, RpcRunnable> rpcDoneMap = new ExpiryLRUMap<>(512, Long.MAX_VALUE, Long.MAX_VALUE, null);
    private final Map<String, RpcServerInstance> serviceInstanceMap = new ConcurrentHashMap<>(8);
    private final List<RpcServerAop> nettyRpcServerAopList = new CopyOnWriteArrayList<>();
    /**
     * Data encoder decoder. (Serialization or Deserialization)
     */
    private DataCodec dataCodec;
    private ChannelHandlerContext context;
    private Supplier<ExecutorService> executorSupplier;
    private ExecutorService executor;

    public RpcServerChannelHandler() {
        this(new JsonDataCodec());
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
                runnable.channelHandler.onStateUpdate(runnable.rpcContext, RpcContext.RpcState.TIMEOUT);
                if (runnable.timeoutInterrupt && runnable.future != null) {
                    runnable.future.cancel(true);
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

    public static RpcContext<RpcServerInstance> getRpcContext() {
        RpcContext<RpcServerInstance> rpcContext = CONTEXT_LOCAL.get();
        if (rpcContext == null) {
            rpcContext = new RpcContext<>();
            CONTEXT_LOCAL.set(rpcContext);
        } else {
            rpcContext.recycle();
        }
        return rpcContext;
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

    public Supplier<ExecutorService> getExecutorSupplier() {
        return executorSupplier;
    }

    public void setExecutorSupplier(Supplier<ExecutorService> executorSupplier) {
        this.executorSupplier = executorSupplier;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.context = ctx;

        RpcContext<RpcServerInstance> rpcContext = getRpcContext();
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
        RpcContext<RpcServerInstance> rpcContext = getRpcContext();
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
        final ExecutorService threadPool = this.executor;
        boolean async = false;
        RpcContext<RpcServerInstance> rpcContext = null;
        try {
            if (packet instanceof RequestPacket) {
                RequestPacket request = (RequestPacket) packet;
                rpcContext = getRpcContext();
                try {
                    rpcContext.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
                    rpcContext.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
                    rpcContext.setRequest(request);

                    // not found instance
                    String serverInstanceKey = RpcServerInstance.getServerInstanceKey(request.getRequestMappingName(), request.getVersion());
                    RpcServerInstance rpcInstance = serviceInstanceMap.get(serverInstanceKey);
                    if (rpcInstance == null) {
                        if (request.getAck() == ACK_YES) {
                            ResponsePacket response = ResponsePacket.newInstance();
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
                        ResponsePacket response = ResponsePacket.newInstance();
                        rpcContext.setResponse(response);
                        response.setRequestId(request.getRequestId());
                        // not found method
                        if (rpcMethod == null) {
                            response.setEncode(DataCodec.Encode.BINARY);
                            response.setStatus(NO_SUCH_METHOD);
                            response.setMessage("not found method [" + request.getMethodName() + "]");
                            response.setData(null);
                            writeAndFlush(request.getAck(), ctx, response, rpcContext);
                        } else if (threadPool != null) {
                            // invoke method by async and call event
                            boolean timeoutInterrupt = rpcMethod.isTimeoutInterrupt();
                            RpcRunnable runnable = new RpcRunnable(rpcMethod, response, timeoutInterrupt, this, rpcInstance, request, rpcContext, ctx);
                            int timeout = choseTimeout(rpcInstance.getTimeout(), rpcMethod.getTimeout(), request.getTimeout());
                            if (timeout > 0) {
                                rpcDoneMap.put(runnable, runnable, timeout);
                            }
                            // execute by rpc thread pool
                            if (timeoutInterrupt) {
                                runnable.future = threadPool.submit(runnable);
                            } else {
                                threadPool.execute(runnable);
                            }
                            async = true;
                        } else {
                            // invoke method by sync
                            CONTEXT_LOCAL.set(rpcContext);
                            rpcInstance.invoke(rpcMethod, response, request, rpcContext, this);
                            writeAndFlush(request.getAck(), ctx, response, rpcContext);
                        }
                    }
                } finally {
                    // call event
                    if (!async) {
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

    public int choseTimeout(Integer serverServiceTimeout, Integer serverMethodTimeout, int clientTimeout) {
        Integer serverTimeout = serverMethodTimeout != null && serverMethodTimeout != -1 ?
                serverMethodTimeout : serverServiceTimeout;

        int timeout;
        if (serverTimeout != null && serverTimeout > 0 && clientTimeout > 0) {
            timeout = Math.min(serverTimeout, clientTimeout);
        } else if (clientTimeout > 0) {
            timeout = clientTimeout;
        } else if (serverTimeout != null && serverTimeout > 0) {
            timeout = serverTimeout;
        } else {
            timeout = clientTimeout;
        }
        return timeout;
    }

    private void onResponseAfter(RpcContext<RpcServerInstance> rpcContext) {
        for (RpcServerAop aop : nettyRpcServerAopList) {
            aop.onResponseAfter(rpcContext);
        }
    }

    private void writeAndFlush(int ack, ChannelHandlerContext channel, ResponsePacket response, RpcContext<RpcServerInstance> rpcContext) {
        boolean release = true;
        try {
            if (ack == ACK_YES) {
                channel.writeAndFlush(response)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                onStateUpdate(rpcContext, RpcContext.RpcState.WRITE_FINISH);
                            } else {
                                future.channel().close();
                            }
                        });
                release = false;
            } else {
                onStateUpdate(rpcContext, RpcContext.RpcState.WRITE_FINISH);
            }
        } finally {
            if (release) {
                RecyclableUtil.release(response);
            }
        }
    }

    public void onStateUpdate(RpcContext<RpcServerInstance> rpcContext, RpcContext.State toState) {
        RpcContext.State formState = rpcContext.getState();
        if (formState != null && formState.isStop()) {
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
        boolean timeoutInterrupt;
        RpcMethod<RpcServerInstance> rpcMethod;
        ResponsePacket response;
        RpcServerChannelHandler channelHandler;
        RpcServerInstance rpcInstance;
        RequestPacket request;
        RpcContext<RpcServerInstance> rpcContext;
        ChannelHandlerContext channel;
        Future future;

        RpcRunnable(RpcMethod<RpcServerInstance> rpcMethod, ResponsePacket response, boolean timeoutInterrupt, RpcServerChannelHandler channelHandler, RpcServerInstance rpcInstance, RequestPacket request, RpcContext<RpcServerInstance> rpcContext, ChannelHandlerContext channel) {
            this.rpcMethod = rpcMethod;
            this.response = response;
            this.timeoutInterrupt = timeoutInterrupt;
            this.channelHandler = channelHandler;
            this.rpcInstance = rpcInstance;
            this.request = request;
            this.rpcContext = rpcContext;
            this.channel = channel;
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
            CONTEXT_LOCAL.set(rpcContext);
            try {
                rpcInstance.invoke(rpcMethod, response, request, rpcContext, channelHandler);
                channelHandler.writeAndFlush(request.getAck(), channel, response, rpcContext);
            } finally {
                try {
                    channelHandler.onResponseAfter(rpcContext);
                } finally {
                    request.recycle();
                    CONTEXT_LOCAL.remove();
                }
            }
        }
    }

}
