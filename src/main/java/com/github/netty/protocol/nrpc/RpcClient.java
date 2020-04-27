package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
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
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcClientAop.CONTEXT_LOCAL;
import static com.github.netty.protocol.nrpc.RpcContext.State.*;
import static com.github.netty.protocol.nrpc.RpcPacket.*;

/**
 * RPC client
 * @author wangzihao
 *  2018/8/18/018
 */
public class RpcClient extends AbstractNettyClient{
    private int idleTimeMs = 5000;
    private int reconnectScheduledIntervalMs = 5000;
    private long connectTimeout = 1000;
    private RpcDBService rpcDBService;
    private RpcCommandService rpcCommandService;
    private final Map<String, Sender> rpcInstanceMap = new HashMap<>(6);
    private final AtomicInteger requestIdIncr = new AtomicInteger();
    private final AtomicBoolean scheduleReconnectTaskIngFlag = new AtomicBoolean(false);
    private final RpcCommandAsyncService rpcCommandAsyncService;

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
    private BiConsumer<Long,RpcClient> reconnectTaskSuccessConsumer;
    private boolean enableRpcHeartLog = true;
    private boolean enableReconnectScheduledTask = false;
    private long reconnectCount = 0;
    protected final DataCodec dataCodec;
    protected final ExpiryLRUMap<Integer, RpcDone> rpcDoneMap = new ExpiryLRUMap<>(512,0.75F,false,Long.MAX_VALUE);
    private final List<RpcClientAop> nettyRpcClientAopList = new CopyOnWriteArrayList<>();

    public RpcClient(String remoteHost, int remotePort) {
        this(new InetSocketAddress(remoteHost, remotePort));
    }

    public RpcClient(InetSocketAddress remoteAddress) {
        this("",remoteAddress);
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress) {
        this(namePre,remoteAddress,new JsonDataCodec());
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress,DataCodec dataCodec) {
        super(namePre + Thread.currentThread().getName()+"-", remoteAddress);
        this.dataCodec = dataCodec;
        dataCodec.getEncodeRequestConsumerList().add(params -> {
            RpcContext<RpcClient> rpcContext = CONTEXT_LOCAL.get();
            for (RpcClientAop aop : nettyRpcClientAopList) {
                aop.onEncodeRequestBefore(rpcContext,params);
            }
        });
        rpcDoneMap.setOnExpiryConsumer(node -> {
            node.getData().doneTimeout(node.getKey(),node.getCreateTimestamp(),node.getExpiryTimestamp());
        });
        this.rpcCommandAsyncService = newInstance(RpcCommandAsyncService.class);
    }

    public DataCodec getDataCodec() {
        return dataCodec;
    }

    public List<RpcClientAop> getAopList() {
        return nettyRpcClientAopList;
    }

    public void onStateUpdate(RpcContext<RpcClient> rpcContext){
        for (RpcClientAop aop : nettyRpcClientAopList) {
            aop.onStateUpdate(rpcContext);
        }
    }

    public boolean isEnableReconnectScheduledTask() {
        return enableReconnectScheduledTask;
    }

    public void setEnableReconnectScheduledTask(boolean enableReconnectScheduledTask) {
        this.enableReconnectScheduledTask = enableReconnectScheduledTask;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setIdleTimeMs(int idleTimeMs) {
        this.idleTimeMs = idleTimeMs;
    }

    public BiConsumer<Long,RpcClient> getReconnectTaskSuccessConsumer() {
        return reconnectTaskSuccessConsumer;
    }

    public void setReconnectTaskSuccessConsumer(BiConsumer<Long,RpcClient> reconnectTaskSuccessConsumer) {
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
     * @param clazz  interface
     * @param <T> type
     * @return  Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz){
        int timeout = Protocol.RpcService.DEFAULT_TIME_OUT;
        String requestMappingName = "";
        String version = "";
        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, Protocol.RpcService.class);
        if (rpcInterfaceAnn != null) {
            timeout = rpcInterfaceAnn.timeout();
            requestMappingName = rpcInterfaceAnn.value();
            version = rpcInterfaceAnn.version();
        }
        if(requestMappingName.isEmpty()){
            requestMappingName = "/"+StringUtil.firstLowerCase(clazz.getSimpleName());
        }
        return newInstance(clazz,timeout,version,requestMappingName,false);
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param version version
     * @param requestMappingName requestMappingName
     * @param methodOverwriteCheck methodOverwriteCheck
     * @param <T> type
     * @return Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz,int timeout,String version,String requestMappingName,boolean methodOverwriteCheck){
        return newInstance(clazz,timeout,version,requestMappingName, new AnnotationMethodToParameterNamesFunction(Collections.singletonList(Protocol.RpcParam.class)),methodOverwriteCheck);
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param version version
     * @param requestMappingName requestMappingName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodOverwriteCheck methodOverwriteCheck
     * @param <T> type
     * @return Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz, int timeout, String version,String requestMappingName, Function<Method,String[]> methodToParameterNamesFunction,boolean methodOverwriteCheck){
        InvocationHandler rpcInstance = newRpcInstance(clazz,timeout,version,requestMappingName,methodToParameterNamesFunction,methodOverwriteCheck);
        Object instance = java.lang.reflect.Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz, Proxy.class}, rpcInstance);
        return (T) instance;
    }

    public static String getClientInstanceKey(Class interfaceClass, String requestMappingName, String version){
        return interfaceClass.getName() + version + requestMappingName;
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param version version
     * @param requestMappingName requestMappingName
     * @param  methodToParameterNamesFunction Method to a function with a parameter name
     * @param methodOverwriteCheck methodOverwriteCheck
     * @return Interface implementation class
     */
    public Sender newRpcInstance(Class clazz, int timeout,String version, String requestMappingName, Function<Method,String[]> methodToParameterNamesFunction,boolean methodOverwriteCheck){
        Map<String, RpcMethod<RpcClient>> rpcMethodMap = RpcMethod.getMethodMap(this,clazz, methodToParameterNamesFunction,methodOverwriteCheck);
        if (rpcMethodMap.isEmpty()) {
            throw new IllegalStateException("The RPC service interface must have at least one method, class=[" + clazz.getSimpleName() + "]");
        }
        Sender rpcInstance = new Sender(this,timeout, requestMappingName,version,rpcMethodMap);
        rpcInstanceMap.put(getClientInstanceKey(clazz,requestMappingName,version),rpcInstance);
        return rpcInstance;
    }

    /**
     * Gets the implementation class
     * @param rpcInstanceKey rpcInstanceKey
     * @return RpcClientInstance
     */
    public Sender getRpcInstance(String rpcInstanceKey) {
        return rpcInstanceMap.get(rpcInstanceKey);
    }

    /**
     * New initialize all
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
                pipeline.addLast(new IdleStateHandler(idleTimeMs, 0, 0,TimeUnit.MILLISECONDS));
                pipeline.addLast(new RpcEncoder());
                pipeline.addLast(new RpcDecoder());
                pipeline.addLast(new ReceiverChannelHandler());
            }
        };
    }

    public boolean scheduleReconnectTask(long reconnectIntervalMillSeconds, TimeUnit timeUnit){
        if(this.scheduleReconnectTaskIngFlag.compareAndSet(false,true)){
            this.reconnectScheduleFuture = getWorker().scheduleWithFixedDelay(() -> {
                if(state == State.UP){
                    cancelScheduleReconnectTask();
                }else {
                    reconnectCount++;
                    connect();
                }
            }, reconnectIntervalMillSeconds,reconnectIntervalMillSeconds, timeUnit);
            return true;
        }
        return false;
    }

    public void cancelScheduleReconnectTask(){
        ScheduledFuture scheduledFuture = this.reconnectScheduleFuture;
        if(scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        BiConsumer<Long,RpcClient> reconnectSuccessHandler = this.reconnectTaskSuccessConsumer;
        if(reconnectSuccessHandler != null){
            reconnectSuccessHandler.accept(reconnectCount,this);
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

    @Override
    public SocketChannel getChannel() throws RpcConnectException{
        SocketChannel socketChannel = super.getChannel();
        if(socketChannel == null){
            long timestamp = System.currentTimeMillis();
            connect().ifPresent(future -> waitGetConnect(future,connectTimeout));
            if(state == State.UP) {
                socketChannel = super.getChannel();
            }else {
                if(enableReconnectScheduledTask) {
                    scheduleReconnectTask(reconnectScheduledIntervalMs, TimeUnit.MILLISECONDS);
                }
                throw new RpcConnectException("The [" + getRemoteAddress() + "] channel no connect. maxConnectTimeout=["+connectTimeout+"], connectTimeout=["+(System.currentTimeMillis() - timestamp)+"]");
            }
        }

        if(!super.isConnectIng() && !socketChannel.isActive()){
            socketChannel.close();
            long timestamp = System.currentTimeMillis();
            connect().ifPresent(future -> waitGetConnect(future,connectTimeout));
            if(state == State.UP) {
                socketChannel = super.getChannel();
            }else {
                if(enableReconnectScheduledTask) {
                    scheduleReconnectTask(reconnectScheduledIntervalMs, TimeUnit.MILLISECONDS);
                }
                throw new RpcConnectException("The [" + socketChannel + "] channel no active. maxConnectTimeout=["+connectTimeout+"], connectTimeout=["+(System.currentTimeMillis() - timestamp)+"]");
            }
        }

        int yieldCount = 0;
        while (!socketChannel.isWritable()){
            socketChannel.flush();
            if(!socketChannel.eventLoop().inEventLoop()) {
                Thread.yield();
                yieldCount++;
            }
        }
        if(yieldCount != 0 && enableRpcHeartLog) {
            logger.info("RpcClient waitWritable... yieldCount={}", yieldCount);
        }
        return socketChannel;
    }

    protected void waitGetConnect(ChannelFuture future, long connectTimeout){
        try {
            long timestamp = System.currentTimeMillis();
            future.await(connectTimeout,TimeUnit.MILLISECONDS);
            //Wake up before the timeout, indicating a successful link,
            //but the state does not necessarily immediately channel with the success of the assignment,
            //to wait for state to UP
            if(System.currentTimeMillis() - timestamp < connectTimeout
                    && !future.channel().eventLoop().inEventLoop()){
                int yieldCount = 0;
                long timeoutTimestamp = timestamp + connectTimeout;
                while (state != State.UP && System.currentTimeMillis() < timeoutTimestamp){
                    yieldCount++;
                    Thread.yield();
                }
                if(enableRpcHeartLog) {
                    logger.info("RpcClient waitGetConnect... yieldCount={}", yieldCount);
                }
            }
        } catch (InterruptedException e) {
            PlatformDependent.throwException(e);
        }
    }

    @Override
    public void setChannel(SocketChannel newChannel) {
        super.setChannel(newChannel);
        state = State.UP;
        //The first initiative to send a package to ensure proper binding agreement
        getRpcCommandAsyncService().ping().subscribe(pingSubscriber);
    }

    private final Subscriber<byte[]> pingSubscriber = new Subscriber<byte[]>() {
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

    public int getIdleTimeMs() {
        return idleTimeMs;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public boolean isConnect() {
        if(rpcCommandService == null){
            return super.isConnect();
        }

        SocketChannel channel = super.getChannel();
        if(channel == null || !channel.isActive()){
            return false;
        }
        try {
            return rpcCommandService.ping() != null;
        }catch (RpcException e){
            return false;
        }
    }

    @Override
    protected void connectAfter(ChannelFuture future) {
        if(future.isSuccess()) {
            if(enableRpcHeartLog) {
                logger.info("RpcClient connect success... {}", future.channel());
            }
            for (RpcClientAop aop : nettyRpcClientAopList) {
                aop.onConnectAfter(this);
            }
        }else {
            if(enableRpcHeartLog){
                logger.info("RpcClient connect fail... {}", future.channel());
            }
        }
    }

    @Override
    protected void stopAfter(ChannelFuture future) {
        rpcInstanceMap.clear();
        rpcCommandService = null;
        rpcDBService = null;

        if(reconnectScheduleFuture != null){
            reconnectScheduleFuture.cancel(false);
        }
        scheduleReconnectTaskIngFlag.set(false);
        if(future.cause() != null){
            logger.error(future.cause().getMessage(),future.cause());
        }
        for (RpcClientAop aop : nettyRpcClientAopList) {
            aop.onDisconnectAfter(this);
        }
    }

    /**
     * Access to data service
     * @return RpcDBService
     */
    public RpcDBService getRpcDBService() {
        if(rpcDBService == null){
            synchronized (this) {
                if(rpcDBService == null) {
                    rpcDBService = newInstance(RpcDBService.class);
                }
            }
        }
        return rpcDBService;
    }

    /**
     * Get command service
     * @return RpcCommandService
     */
    public RpcCommandService getRpcCommandService() {
        if(rpcCommandService == null){
            synchronized (this) {
                if(rpcCommandService == null) {
                    rpcCommandService = newInstance(RpcCommandService.class);
                }
            }
        }
        return rpcCommandService;
    }

    /**
     * Get command async service
     * @return RpcCommandAsyncService
     */
    public RpcCommandAsyncService getRpcCommandAsyncService() {
        return rpcCommandAsyncService;
    }

    /**
     * Get connection status
     * @return State
     */
    public State getState() {
        return state;
    }

    protected int newRequestId(){
        int id = requestIdIncr.getAndIncrement();
        if(id < 0){
            id = 0;
            requestIdIncr.set(id);
        }
        return id;
    }

    /**
     * Client connection status
     */
    public enum State{
        DOWN,
        UP
    }

    /**
     * proxy flag
     */
    public interface Proxy {}

    @Override
    public String toString() {
        return super.toString()+"{" +
                "state=" + state +
                '}';
    }

    class ReceiverChannelHandler extends AbstractChannelHandler<RpcPacket,Object> {
        ReceiverChannelHandler() {
            super(false);
        }

        @Override
        protected void onMessageReceived(ChannelHandlerContext ctx, RpcPacket packet) throws Exception {
            if(packet instanceof ResponsePacket) {
                ResponsePacket rpcResponse = (ResponsePacket) packet;

                RpcDone rpcDone = rpcDoneMap.remove(rpcResponse.getRequestId());
                if (rpcDone != null) {
                    //Handed over to the thread that sent the message
                    rpcDone.done(rpcResponse);
                }
            }else {
                logger.debug("client received packet={}",String.valueOf(packet));
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
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            state = State.DOWN;
            if(enableReconnectScheduledTask) {
                scheduleReconnectTask(reconnectScheduledIntervalMs, TimeUnit.MILLISECONDS);
            }
        }

        private final Subscriber<byte[]> readerIdlePingHandler = new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(byte[] bytes) {
                if(state != State.UP) {
                    state = State.UP;
                }
                if(enableRpcHeartLog){
                    logger.info("RpcClient heart UP by readerIdle {}...{}", new String(bytes),RpcClient.super.getChannel());
                }
            }

            @Override
            public void onError(Throwable t) {
                if(state != State.DOWN) {
                    state = State.DOWN;
                }
                SocketChannel channel = RpcClient.super.getChannel();
                if(channel != null) {
                    channel.close();
                }
                if(enableRpcHeartLog){
                    logger.info("RpcClient heart DOWN by readerIdle ...{} {}", RpcClient.super.getChannel(),t.toString());
                }
            }

            @Override
            public void onComplete() {}
        };

    }

    public static class Sender implements InvocationHandler {
        private static final LoggerX logger = LoggerFactoryX.getLogger(Sender.class);
        private int timeout;
        private final String requestMappingName;
        private final String version;
        private final Map<String, RpcMethod<RpcClient>> rpcMethodMap;
        private final RpcClient rpcClient;
        private Sender(RpcClient rpcClient,int timeout, String requestMappingName,String version, Map<String, RpcMethod<RpcClient>> rpcMethodMap) {
            this.rpcClient = rpcClient;
            this.rpcMethodMap = rpcMethodMap;
            this.timeout = timeout;
            this.version = version;
            this.requestMappingName = requestMappingName;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
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
            if(rpcMethod.isReturnTypeReactivePublisherFlag()){
                RpcContext<RpcClient> rpcContext = new RpcContext<>();
                rpcContext.setArgs(args);
                rpcContext.setRpcMethod(rpcMethod);
                result = new RpcClientReactivePublisher(rpcContext,requestMappingName,version,timeout);
            }else if(rpcMethod.isReturnTypeJdk9PublisherFlag()){
                throw new UnsupportedOperationException("now version no support return type java.util.concurrent.Flow.Publisher. The future version will support. ");
//                RpcContext<RpcClient> rpcContext = new RpcContext<>();
//                rpcContext.setArgs(args);
//                rpcContext.setRpcMethod(rpcMethod);
//                rpcContext.setState(INIT);
//                result = new RpcClientJdk9Publisher(rpcContext,requestMappingName);
            }else {
                RpcContext<RpcClient> rpcContext = CONTEXT_LOCAL.get();
                if(rpcContext == null){
                    rpcContext = new RpcContext<>();
                    CONTEXT_LOCAL.set(rpcContext);
                }else {
                    rpcContext.recycle();
                }
                try {
                    rpcContext.setArgs(args);
                    rpcContext.setRpcMethod(rpcMethod);
                    result = requestSync(rpcContext);
                }finally {
                    CONTEXT_LOCAL.set(null);
                }
            }
            return result;
        }

        private Object requestSync(RpcContext<RpcClient> rpcContext) throws Throwable {
            RpcMethod method = rpcContext.getRpcMethod();
            Class<?> returnType = method.getMethod().getReturnType();
            byte ackFlag = returnType == void.class? ACK_NO : ACK_YES;

            int requestId = rpcClient.newRequestId();
            RequestPacket rpcRequest = RequestPacket.newInstance();
            rpcRequest.setRequestId(requestId);
            rpcRequest.setRequestMappingName(requestMappingName);
            rpcRequest.setVersion(version);
            rpcRequest.setMethodName(method.getMethodDescriptorName());
            rpcRequest.setAck(ackFlag);

            rpcContext.setRequest(rpcRequest);
            rpcContext.setState(INIT);
            rpcClient.onStateUpdate(rpcContext);

            rpcRequest.setData(rpcClient.dataCodec.encodeRequestData(rpcContext.getArgs(), rpcContext.getRpcMethod()));
            rpcContext.setState(WRITE_ING);
            rpcClient.onStateUpdate(rpcContext);

            RpcClientFuture future = null;
            try {
                SocketChannel channel = rpcClient.getChannel();
                rpcContext.setRemoteAddress(channel.remoteAddress());
                rpcContext.setLocalAddress(channel.localAddress());
                if (ackFlag == ACK_YES) {
                    future = RpcClientFuture.newInstance(rpcContext);
                    rpcClient.rpcDoneMap.put(requestId, future);
                }
                channel.writeAndFlush(rpcRequest).addListener((ChannelFutureListener) channelFuture -> {
                    if(rpcContext.getState() == INIT){
                        logger.warn("on timeout after. write event. isSuccess={},channel={}",
                                channelFuture.isSuccess(),channelFuture.channel());
                        return;
                    }
                    CONTEXT_LOCAL.set(rpcContext);
                    try {
                        if (channelFuture.isSuccess()) {
                            rpcContext.setState(WRITE_FINISH);
                            rpcClient.onStateUpdate(rpcContext);
                        } else {
                            channelFuture.channel().close().addListener(f -> rpcClient.connect());
                            rpcContext.setThrowable(channelFuture.cause());
                        }
                    }finally {
                        CONTEXT_LOCAL.set(null);
                    }
                });
            }catch (RpcException rpcException){
                rpcContext.setThrowable(rpcException);
            }

            Object result = null;
            ResponsePacket rpcResponse = null;
            try {
                Throwable throwable = rpcContext.getThrowable();
                if(throwable instanceof RpcException){
                    throw throwable;
                }else if(throwable != null) {
                    throw new RpcWriteException("rpc write exception. " + throwable, throwable);
                }
                if (future != null) {
                    rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
                    rpcContext.setResponse(rpcResponse);
                    rpcContext.setState(READ_ING);
                    rpcClient.onStateUpdate(rpcContext);

                    //If the server is not encoded, return directly
                    if (rpcResponse.getEncode() == BINARY) {
                        result = rpcResponse.getData();
                    } else {
                        result = rpcClient.dataCodec.decodeResponseData(rpcResponse.getData(), rpcContext.getRpcMethod());
                    }
                    rpcContext.setResult(result);
                    rpcContext.setState(READ_FINISH);
                    rpcClient.onStateUpdate(rpcContext);
                }
            }catch (Throwable e){
                if(e instanceof RpcTimeoutException){
                    rpcContext.setState(TIMEOUT);
                }
                rpcContext.setThrowable(e);
                throw e;
            }finally {
                if(future != null) {
                    rpcClient.rpcDoneMap.remove(requestId);
                }
                try {
                    boolean isTimeout = rpcContext.getState() == TIMEOUT;
                    for (RpcClientAop aop : rpcClient.nettyRpcClientAopList) {
                        if(isTimeout){
                            aop.onTimeout(rpcContext);
                        }else {
                            aop.onResponseAfter(rpcContext);
                        }
                    }
                }finally {
                    RecyclableUtil.release(rpcResponse);
                    if(future != null) {
                        future.recycle();
                    }
                    rpcContext.recycle();
                }
            }
            return result;
        }
    }

    public static long getTotalInvokeCount() {
        return RpcClientFuture.TOTAL_COUNT.sum();
    }

    public static long getTotalTimeoutCount() {
        return RpcClientFuture.TOTAL_COUNT.sum() - RpcClientFuture.TOTAL_SUCCESS_COUNT.sum();
    }
}
