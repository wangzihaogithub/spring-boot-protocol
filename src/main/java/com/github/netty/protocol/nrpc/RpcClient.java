package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.util.*;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;
import com.github.netty.protocol.nrpc.exception.RpcWriteException;
import com.github.netty.protocol.nrpc.service.RpcCommandAsyncService;
import com.github.netty.protocol.nrpc.service.RpcCommandService;
import com.github.netty.protocol.nrpc.service.RpcDBService;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.internal.PlatformDependent;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcClientAop.CONTEXT_LOCAL;
import static com.github.netty.protocol.nrpc.RpcContext.RpcState.*;
import static com.github.netty.protocol.nrpc.RpcPacket.*;

/**
 * RPC client
 *
 * @author wangzihao
 * 2018/8/18/018
 */
public class RpcClient extends AbstractNettyClient {
    protected final DataCodec dataCodec;
    protected final ExpiryLRUMap<Integer, RpcDone> rpcDoneMap = new ExpiryLRUMap<>(512, Long.MAX_VALUE, Long.MAX_VALUE, null);
    private final Map<String, Sender> rpcInstanceMap = new LinkedHashMap<>(6);
    private final AtomicInteger requestIdIncr = new AtomicInteger();
    private final AtomicBoolean scheduleReconnectTaskIngFlag = new AtomicBoolean(false);
    private final RpcCommandAsyncService rpcCommandAsyncService;
    private final List<RpcClientAop> nettyRpcClientAopList = new CopyOnWriteArrayList<>();
    private static final Subscriber<byte[]> pingSubscriber = new Subscriber<byte[]>() {
        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
        }

        @Override
        public void onNext(byte[] bytes) {

        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {

        }
    };
    private int idleTimeMs = 5000;
    private int reconnectScheduledIntervalMs = 5000;
    private long connectTimeout = 1000;
    private RpcDBService rpcDBService;
    private RpcCommandService rpcCommandService;
    /**
     * Connecting timeout timestamp
     */
    private volatile long connectTimeoutTimestamp;
    /**
     * Connection status
     */
    private volatile State state = State.DOWN;
    /**
     * reconnectScheduleFuture
     */
    private ScheduledFuture<?> reconnectScheduleFuture;
    /**
     * reconnectTaskSuccessConsumer Callback method after successful reconnect
     */
    private BiConsumer<Long, RpcClient> reconnectTaskSuccessConsumer;
    private boolean enableRpcHeartLog = true;
    private boolean enableReconnectScheduledTask = false;
    private long reconnectCount = 0;

    public RpcClient(String remoteHost, int remotePort) {
        this(new InetSocketAddress(remoteHost, remotePort));
    }

    public RpcClient(InetSocketAddress remoteAddress) {
        this("", remoteAddress);
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress) {
        this(namePre, remoteAddress, new JsonDataCodec());
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress, DataCodec dataCodec) {
        super(namePre + Thread.currentThread().getName() + "-", remoteAddress);
        this.dataCodec = dataCodec;
        dataCodec.getEncodeRequestConsumerList().add(params -> {
            RpcContext<RpcClient> rpcContext = CONTEXT_LOCAL.get();
            for (RpcClientAop aop : nettyRpcClientAopList) {
                aop.onEncodeRequestBefore(rpcContext, params);
            }
        });
        rpcDoneMap.setOnExpiryConsumer(node -> {
            try {
                node.getData().doneTimeout(node.getKey(), node.getCreateTimestamp(), node.getExpiryTimestamp());
            } catch (Exception e) {
                logger.warn("doneTimeout exception. client = {}, message = {}.", this, e.toString(), e);
            }
        });
        this.rpcCommandAsyncService = newInstance(RpcCommandAsyncService.class);
    }

    public static String getClientInstanceKey(Class interfaceClass, String requestMappingName, String version) {
        return interfaceClass.getName() + version + requestMappingName;
    }

    public static long getTotalInvokeCount() {
        return RpcClientFuture.TOTAL_COUNT.sum();
    }

    public static long getTotalTimeoutCount() {
        return RpcClientFuture.TOTAL_COUNT.sum() - RpcClientFuture.TOTAL_SUCCESS_COUNT.sum();
    }

    public DataCodec getDataCodec() {
        return dataCodec;
    }

    public List<RpcClientAop> getAopList() {
        return nettyRpcClientAopList;
    }

    public void onStateUpdate(RpcContext<RpcClient> rpcContext, RpcContext.State toState) {
        RpcContext.State formState = rpcContext.getState();
        if (formState != null && formState.isStop()) {
            return;
        }
        rpcContext.setState(toState);
        for (RpcClientAop aop : nettyRpcClientAopList) {
            aop.onStateUpdate(rpcContext, formState, toState);
        }
    }

    public boolean isEnableReconnectScheduledTask() {
        return enableReconnectScheduledTask;
    }

    public void setEnableReconnectScheduledTask(boolean enableReconnectScheduledTask) {
        this.enableReconnectScheduledTask = enableReconnectScheduledTask;
    }

    public BiConsumer<Long, RpcClient> getReconnectTaskSuccessConsumer() {
        return reconnectTaskSuccessConsumer;
    }

    public void setReconnectTaskSuccessConsumer(BiConsumer<Long, RpcClient> reconnectTaskSuccessConsumer) {
        this.reconnectTaskSuccessConsumer = reconnectTaskSuccessConsumer;
    }

    public boolean isEnableRpcHeartLog() {
        return enableRpcHeartLog;
    }

    public void setEnableRpcHeartLog(boolean enableRpcHeartLog) {
        this.enableRpcHeartLog = enableRpcHeartLog;
    }

    public int getReconnectScheduledIntervalMs() {
        return reconnectScheduledIntervalMs;
    }

    public void setReconnectScheduledIntervalMs(int reconnectScheduledIntervalMs) {
        this.reconnectScheduledIntervalMs = reconnectScheduledIntervalMs;
    }

    /**
     * New implementation class
     *
     * @param clazz interface
     * @param <T>   type
     * @return Interface implementation class
     */
    public <T> T newInstance(Class<T> clazz) {
        int timeout = NRpcService.DEFAULT_TIME_OUT;
        String requestMappingName = "";
        String version = "";
        NRpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, NRpcService.class);
        if (rpcInterfaceAnn != null) {
            timeout = rpcInterfaceAnn.timeout();
            requestMappingName = rpcInterfaceAnn.value();
            version = rpcInterfaceAnn.version();
        }
        if (requestMappingName.isEmpty()) {
            requestMappingName = "/" + StringUtil.firstLowerCase(clazz.getSimpleName());
        }
        return newInstance(clazz, timeout, version, requestMappingName, false);
    }

    /**
     * New implementation class
     *
     * @param clazz                interface
     * @param timeout              timeout
     * @param version              version
     * @param requestMappingName   requestMappingName
     * @param methodOverwriteCheck methodOverwriteCheck
     * @param <T>                  type
     * @return Interface implementation class
     */
    public <T> T newInstance(Class<T> clazz, int timeout, String version, String requestMappingName, boolean methodOverwriteCheck) {
        return newInstance(clazz, timeout, version, requestMappingName, new AnnotationMethodToParameterNamesFunction(NRpcParam.class),
                new AnnotationMethodToMethodNameFunction(NRpcMethod.class),
                methodOverwriteCheck);
    }

    /**
     * New implementation class
     *
     * @param clazz                          interface
     * @param timeout                        timeout
     * @param version                        version
     * @param requestMappingName             requestMappingName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodToNameFunction           Method of extracting remote call method name
     * @param methodOverwriteCheck           methodOverwriteCheck
     * @param <T>                            type
     * @return Interface implementation class
     */
    public <T> T newInstance(Class<T> clazz, int timeout, String version, String requestMappingName, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean methodOverwriteCheck) {
        InvocationHandler rpcInstance = newRpcInstance(clazz, timeout, version, requestMappingName, methodToParameterNamesFunction, methodToNameFunction, methodOverwriteCheck);
        Object instance = java.lang.reflect.Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz, Proxy.class}, rpcInstance);
        return (T) instance;
    }

    /**
     * New implementation class
     *
     * @param clazz                          interface
     * @param timeout                        timeout
     * @param version                        version
     * @param requestMappingName             requestMappingName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodToNameFunction           Method of extracting remote call method name
     * @param methodOverwriteCheck           methodOverwriteCheck
     * @return Interface implementation class
     */
    public Sender newRpcInstance(Class clazz, int timeout, String version, String requestMappingName, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean methodOverwriteCheck) {
        Map<String, RpcMethod<RpcClient>> rpcMethodMap = RpcMethod.getMethodMap(this, clazz, methodToParameterNamesFunction, methodToNameFunction, methodOverwriteCheck);
        if (rpcMethodMap.isEmpty()) {
            throw new IllegalStateException("The RPC service interface must have at least one method, class=[" + clazz.getSimpleName() + "]");
        }
        Sender rpcInstance = new Sender(this, timeout, requestMappingName, version, rpcMethodMap);
        rpcInstanceMap.put(getClientInstanceKey(clazz, requestMappingName, version), rpcInstance);
        return rpcInstance;
    }

    /**
     * Gets the implementation class
     *
     * @param rpcInstanceKey rpcInstanceKey
     * @return RpcClientInstance
     */
    public Sender getRpcInstance(String rpcInstanceKey) {
        return rpcInstanceMap.get(rpcInstanceKey);
    }

    /**
     * New initialize all
     *
     * @return ChannelInitializer
     */
    @Override
    protected ChannelInitializer<? extends Channel> newBossChannelHandler() {
        for (RpcClientAop aop : nettyRpcClientAopList) {
            aop.onInitAfter(this);
        }
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast(new IdleStateHandler(idleTimeMs, 0, 0, TimeUnit.MILLISECONDS));
                pipeline.addLast(new RpcEncoder());
                pipeline.addLast(new RpcDecoder());
                pipeline.addLast(new ReceiverChannelHandler());
            }
        };
    }

    public boolean scheduleReconnectTask(long reconnectIntervalMillSeconds, TimeUnit timeUnit) {
        if (this.scheduleReconnectTaskIngFlag.compareAndSet(false, true)) {
            this.reconnectScheduleFuture = getWorker().scheduleWithFixedDelay(() -> {
                if (state == State.UP) {
                    cancelScheduleReconnectTask();
                } else {
                    reconnectCount++;
                    connect();
                }
            }, reconnectIntervalMillSeconds, reconnectIntervalMillSeconds, timeUnit);
            return true;
        }
        return false;
    }

    public void cancelScheduleReconnectTask() {
        ScheduledFuture scheduledFuture = this.reconnectScheduleFuture;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        BiConsumer<Long, RpcClient> reconnectSuccessHandler = this.reconnectTaskSuccessConsumer;
        if (reconnectSuccessHandler != null) {
            reconnectSuccessHandler.accept(reconnectCount, this);
        }
        this.reconnectScheduleFuture = null;
        this.reconnectCount = 0;
        this.scheduleReconnectTaskIngFlag.set(false);
    }

    public boolean isScheduleReconnectTaskIng() {
        return scheduleReconnectTaskIngFlag.get();
    }

    public ExpiryLRUMap<Integer, RpcDone> getRpcDoneMap() {
        return rpcDoneMap;
    }

    public SocketChannel channel() {
        return super.getChannel();
    }

    @Override
    public SocketChannel getChannel() throws RpcConnectException {
        SocketChannel socketChannel = super.getChannel();
        if (socketChannel == null || !socketChannel.isActive()) {
            long timestamp = System.currentTimeMillis();
            socketChannel = waitGetConnect(connect(), connectTimeout);
            if (!socketChannel.isActive()) {
                if (enableReconnectScheduledTask) {
                    scheduleReconnectTask(reconnectScheduledIntervalMs, TimeUnit.MILLISECONDS);
                }
                throw new RpcConnectException("The [" + socketChannel + "] channel no connect. maxConnectTimeout=[" + connectTimeout + "], connectTimeout=[" + (System.currentTimeMillis() - timestamp) + "]");
            }
        }

        int yieldCount = 0;
        if (!socketChannel.isWritable()) {
            socketChannel.flush();
        }
        while (!socketChannel.isWritable()) {
            ChannelUtils.forceFlush(socketChannel);
            if (!socketChannel.eventLoop().inEventLoop()) {
                Thread.yield();
                yieldCount++;
            }
        }
        if (yieldCount != 0 && enableRpcHeartLog) {
            logger.info("RpcClient waitWritable... yieldCount={}", yieldCount);
        }
        return socketChannel;
    }

    @Override
    public void setChannel(SocketChannel newChannel) {
        super.setChannel(newChannel);
        state = State.UP;
        //The first initiative to send a package to ensure proper binding agreement
        getRpcCommandAsyncService().ping().subscribe(pingSubscriber);
    }

    protected SocketChannel waitGetConnect(Optional<ChannelFuture> optional, long connectTimeout) {
        if (optional.isPresent()) {
            connectTimeoutTimestamp = System.currentTimeMillis();
            ChannelFuture future = optional.get();
            try {
                future.await(connectTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                PlatformDependent.throwException(e);
            } finally {
                connectTimeoutTimestamp = 0;
            }
            return (SocketChannel) future.channel();
        } else {
            int yieldCount = 0;
            long timeoutTimestamp = connectTimeoutTimestamp;
            long waitTime;
            while (timeoutTimestamp != 0 && (waitTime = timeoutTimestamp - System.currentTimeMillis()) > 0) {
                if (waitTime > 200) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        PlatformDependent.throwException(e);
                    }
                } else {
                    yieldCount++;
                    Thread.yield();
                }
            }
            while (state != State.UP) {
                yieldCount++;
                Thread.yield();
            }
            if (enableRpcHeartLog) {
                logger.info("RpcClient waitGetConnect... yieldCount={}", yieldCount);
            }
            return super.getChannel();
        }
    }

    public int getIdleTimeMs() {
        return idleTimeMs;
    }

    public void setIdleTimeMs(int idleTimeMs) {
        this.idleTimeMs = idleTimeMs;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    @Override
    public boolean isConnect() {
        if (rpcCommandService == null) {
            return super.isConnect();
        }

        SocketChannel channel = super.getChannel();
        if (channel == null || !channel.isActive()) {
            return false;
        }
        try {
            return rpcCommandService.ping() != null;
        } catch (RpcException e) {
            return false;
        }
    }

    @Override
    protected void connectAfter(ChannelFuture future) {
        if (future.isSuccess()) {
            if (enableRpcHeartLog) {
                logger.info("RpcClient connect success... {}", future.channel());
            }
        } else {
            if (enableRpcHeartLog) {
                logger.info("RpcClient connect fail... {}", future.channel());
            }
        }
    }

    @Override
    protected void stopAfter(ChannelFuture future) {
        rpcInstanceMap.clear();
        rpcCommandService = null;
        rpcDBService = null;

        if (reconnectScheduleFuture != null) {
            reconnectScheduleFuture.cancel(false);
        }
        scheduleReconnectTaskIngFlag.set(false);
        if (future.cause() != null) {
            logger.error(future.cause().getMessage(), future.cause());
        }
    }

    /**
     * Access to data service
     *
     * @return RpcDBService
     */
    public RpcDBService getRpcDBService() {
        if (rpcDBService == null) {
            synchronized (this) {
                if (rpcDBService == null) {
                    rpcDBService = newInstance(RpcDBService.class);
                }
            }
        }
        return rpcDBService;
    }

    /**
     * Get command service
     *
     * @return RpcCommandService
     */
    public RpcCommandService getRpcCommandService() {
        if (rpcCommandService == null) {
            synchronized (this) {
                if (rpcCommandService == null) {
                    rpcCommandService = newInstance(RpcCommandService.class);
                }
            }
        }
        return rpcCommandService;
    }

    /**
     * Get command async service
     *
     * @return RpcCommandAsyncService
     */
    public RpcCommandAsyncService getRpcCommandAsyncService() {
        return rpcCommandAsyncService;
    }

    /**
     * Get connection status
     *
     * @return State
     */
    public State getState() {
        return state;
    }

    protected int newRequestId() {
        int id = requestIdIncr.getAndIncrement();
        if (id < 0) {
            id = 0;
            requestIdIncr.set(id);
        }
        return id;
    }

    @Override
    public String toString() {
        return super.toString() + "{" +
                "state=" + state +
                '}';
    }

    /**
     * Client connection status
     */
    public enum State {
        DOWN,
        UP
    }

    /**
     * proxy flag
     */
    public interface Proxy {
    }

    public static class Sender implements InvocationHandler {
        private static final LoggerX logger = LoggerFactoryX.getLogger(Sender.class);
        private final String requestMappingName;
        private final String version;
        private final Map<String, RpcMethod<RpcClient>> rpcMethodMap;
        private final RpcClient rpcClient;
        private int timeout;

        private Sender(RpcClient rpcClient, int timeout, String requestMappingName, String version, Map<String, RpcMethod<RpcClient>> rpcMethodMap) {
            this.rpcClient = rpcClient;
            this.rpcMethodMap = rpcMethodMap;
            this.timeout = timeout;
            this.version = version;
            this.requestMappingName = requestMappingName;
        }

        public Map<String, RpcMethod<RpcClient>> getRpcMethodMap() {
            return rpcMethodMap;
        }

        public String getRequestMappingName() {
            return requestMappingName;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public String getVersion() {
            return version;
        }

        public RpcClient getRpcClient() {
            return rpcClient;
        }

        /**
         * Make RPC calls
         *
         * @param proxy  proxy
         * @param method method
         * @param args   args
         * @return Object
         * @throws Throwable Throwable
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            int parameterCount = method.getParameterCount();
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            if ("toString".equals(methodName) && parameterCount == 0) {
                return this.toString();
            }
            if ("hashCode".equals(methodName) && parameterCount == 0) {
                return this.hashCode();
            }
            if ("equals".equals(methodName) && parameterCount == 1) {
                return this.equals(args[0]);
            }

            String rpcMethodName = RpcMethod.getMethodDescriptorName(method);
            RpcMethod<RpcClient> rpcMethod = rpcMethodMap.get(rpcMethodName);
            if (rpcMethod == null) {
                return null;
            }

            Object result;
            if (rpcMethod.isReturnRxjava3ObservableFlag()) {
                RpcContext<RpcClient> rpcContext = new RpcContext<>();
                rpcContext.setArgs(args);
                rpcContext.setRpcMethod(rpcMethod);
                result = new RpcClientRxjava3Observable(new RpcClientReactivePublisher(rpcContext, requestMappingName, version, timeout));
            } else if (rpcMethod.isReturnRxjava3FlowableFlag()) {
                RpcContext<RpcClient> rpcContext = new RpcContext<>();
                rpcContext.setArgs(args);
                rpcContext.setRpcMethod(rpcMethod);
                result = new RpcClientRxjava3Flowable(new RpcClientReactivePublisher(rpcContext, requestMappingName, version, timeout));
            } else if (rpcMethod.isReturnTypeJdk9PublisherFlag()) {
                throw new UnsupportedOperationException("now version no support return type java.util.concurrent.Flow.Publisher. The future version will support. ");
//                RpcContext<RpcClient> rpcContext = new RpcContext<>();
//                rpcContext.setArgs(args);
//                rpcContext.setRpcMethod(rpcMethod);
//                rpcContext.setState(INIT);
//                result = new RpcClientJdk9Publisher(rpcContext,requestMappingName);
            } else if (rpcMethod.isReturnTypeReactivePublisherFlag()) {
                RpcContext<RpcClient> rpcContext = new RpcContext<>();
                rpcContext.setArgs(args);
                rpcContext.setRpcMethod(rpcMethod);
                result = new RpcClientReactivePublisher(rpcContext, requestMappingName, version, timeout);
            } else if (rpcMethod.isReturnCompletableFutureFlag() || rpcMethod.isReturnFutureFlag() || rpcMethod.isReturnCompletionStageFlag()) {
                RpcContext<RpcClient> rpcContext = new RpcContext<>();
                rpcContext.setArgs(args);
                rpcContext.setRpcMethod(rpcMethod);
                result = new RpcClientCompletableFuture(new RpcClientReactivePublisher(rpcContext, requestMappingName, version, timeout));
            } else {
                RpcContext<RpcClient> rpcContext = CONTEXT_LOCAL.get();
                if (rpcContext == null) {
                    rpcContext = new RpcContext<>();
                    CONTEXT_LOCAL.set(rpcContext);
                } else {
                    rpcContext.recycle();
                }
                try {
                    rpcContext.setRpcBeginTimestamp(System.currentTimeMillis());
                    rpcContext.setArgs(args);
                    rpcContext.setRpcMethod(rpcMethod);
                    result = requestSync(rpcContext);
                } finally {
                    CONTEXT_LOCAL.set(null);
                }
            }
            return result;
        }

        private Object requestSync(RpcContext<RpcClient> rpcContext) throws Throwable {
            RpcMethod method = rpcContext.getRpcMethod();
            byte ackFlag = method.isReturnVoid() ? ACK_NO : ACK_YES;

            int requestId = rpcClient.newRequestId();
            RequestPacket rpcRequest = RequestPacket.newInstance();
            rpcRequest.setRequestId(requestId);
            rpcRequest.setRequestMappingName(requestMappingName);
            rpcRequest.setVersion(version);
            rpcRequest.setMethodName(method.getMethodName());
            rpcRequest.setAck(ackFlag);
            rpcRequest.setTimeout(timeout);

            rpcContext.setRequest(rpcRequest);
            rpcClient.onStateUpdate(rpcContext, INIT);

            rpcRequest.setData(rpcClient.dataCodec.encodeRequestData(rpcContext.getArgs(), rpcContext.getRpcMethod()));
            rpcClient.onStateUpdate(rpcContext, WRITE_ING);

            RpcClientFuture future = null;
            try {
                rpcContext.setRemoteAddress(rpcClient.getRemoteAddress());
                SocketChannel channel = rpcClient.getChannel();
                rpcContext.setRemoteAddress(channel.remoteAddress());
                rpcContext.setLocalAddress(channel.localAddress());
                if (ackFlag == ACK_YES) {
                    future = RpcClientFuture.newInstance(rpcContext);
                    rpcClient.rpcDoneMap.put(requestId, future);
                }
                rpcRequest.setTimeout(timeout);
                channel.writeAndFlush(rpcRequest).addListener((ChannelFutureListener) channelFuture -> {
                    if (rpcContext.getState() == INIT) {
                        logger.warn("on timeout after. write event. isSuccess={},channel={}",
                                channelFuture.isSuccess(), channelFuture.channel());
                        return;
                    }
                    CONTEXT_LOCAL.set(rpcContext);
                    try {
                        if (channelFuture.isSuccess()) {
                            rpcClient.onStateUpdate(rpcContext, WRITE_FINISH);
                        } else {
                            channelFuture.channel().close().addListener(f -> rpcClient.connect());
                            rpcContext.setThrowable(channelFuture.cause());
                        }
                    } finally {
                        CONTEXT_LOCAL.set(null);
                    }
                });
            } catch (RpcException rpcException) {
                rpcContext.setThrowable(rpcException);
            }

            Object result = null;
            ResponsePacket rpcResponse = null;
            try {
                Throwable throwable = rpcContext.getThrowable();
                if (throwable instanceof RpcException) {
                    throw throwable;
                } else if (throwable != null) {
                    throw new RpcWriteException("rpc write exception. " + throwable, throwable);
                }
                if (future != null) {
                    try {
                        rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
                    } finally {
                        rpcContext.setRpcEndTimestamp(System.currentTimeMillis());
                    }
                    rpcContext.setResponse(rpcResponse);
                    rpcClient.onStateUpdate(rpcContext, READ_ING);

                    //If the server is not encoded, return directly
                    if (rpcResponse.getEncode() == BINARY) {
                        result = rpcResponse.getData();
                    } else {
                        result = rpcClient.dataCodec.decodeResponseData(rpcResponse.getData(), rpcContext.getRpcMethod());
                    }
                    rpcContext.setResult(result);
                    rpcClient.onStateUpdate(rpcContext, READ_FINISH);
                }
            } catch (Throwable e) {
                if (e instanceof RpcTimeoutException) {
                    rpcClient.onStateUpdate(rpcContext, TIMEOUT);
                }
                rpcContext.setThrowable(e);
                throw e;
            } finally {
                if (future != null) {
                    rpcClient.rpcDoneMap.remove(requestId);
                }
                try {
                    boolean isTimeout = rpcContext.getState() == TIMEOUT;
                    for (RpcClientAop aop : rpcClient.nettyRpcClientAopList) {
                        if (isTimeout) {
                            aop.onTimeout(rpcContext);
                        } else {
                            aop.onResponseAfter(rpcContext);
                        }
                    }
                } finally {
                    RecyclableUtil.release(rpcResponse);
                    if (future != null) {
                        future.recycle();
                    }
                    rpcContext.recycle();
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return "Sender{" +
                    "requestMappingName='" + requestMappingName + '\'' +
                    ", version='" + version + '\'' +
                    ", timeout=" + timeout +
                    ", state=" + rpcClient.getState() +
                    ", channel=" + rpcClient.channel() +
                    '}';
        }
    }

    class ReceiverChannelHandler extends AbstractChannelHandler<RpcPacket, Object> {
        private final Subscriber<byte[]> readerIdlePingHandler = new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(byte[] bytes) {
                if (state != State.UP) {
                    state = State.UP;
                }
                if (enableRpcHeartLog) {
                    logger.info("RpcClient heart UP by readerIdle {}...{}", new String(bytes), RpcClient.super.getChannel());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (state != State.DOWN) {
                    state = State.DOWN;
                }
                SocketChannel channel = RpcClient.super.getChannel();
                if (channel != null) {
                    channel.close();
                }
                if (enableRpcHeartLog) {
                    logger.info("RpcClient heart DOWN by readerIdle ...{} {}", RpcClient.super.getChannel(), t.toString());
                }
            }

            @Override
            public void onComplete() {
            }
        };

        ReceiverChannelHandler() {
            super(false);
        }

        @Override
        protected void onMessageReceived(ChannelHandlerContext ctx, RpcPacket packet) throws Exception {
            if (packet instanceof ResponsePacket) {
                ResponsePacket rpcResponse = (ResponsePacket) packet;

                RpcDone rpcDone = rpcDoneMap.remove(rpcResponse.getRequestId());
                if (rpcDone != null) {
                    //Handed over to the thread that sent the message
                    rpcDone.done(rpcResponse);
                }
            } else {
                logger.debug("client received packet={}", String.valueOf(packet));
                packet.recycle();
            }
        }

        @Override
        protected void onReaderIdle(ChannelHandlerContext ctx) {
            //heart beat
            getRpcCommandAsyncService().ping().subscribe(readerIdlePingHandler);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            state = State.UP;
            for (RpcClientAop aop : nettyRpcClientAopList) {
                aop.onConnectAfter(RpcClient.this);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            state = State.DOWN;
            if (enableReconnectScheduledTask) {
                scheduleReconnectTask(reconnectScheduledIntervalMs, TimeUnit.MILLISECONDS);
            }
            for (RpcClientAop aop : nettyRpcClientAopList) {
                aop.onDisconnectAfter(RpcClient.this);
            }
        }

    }
}
