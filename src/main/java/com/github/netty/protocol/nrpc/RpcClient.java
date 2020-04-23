package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.util.*;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.exception.RpcWriteException;
import com.github.netty.protocol.nrpc.service.RpcCommandAsyncService;
import com.github.netty.protocol.nrpc.service.RpcCommandService;
import com.github.netty.protocol.nrpc.service.RpcDBService;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
    private final Map<String, Sender> rpcInstanceMap = new HashMap<>(6);
    private int idleTime = 10;
    private RpcCommandService rpcCommandService;
    private RpcCommandAsyncService rpcCommandAsyncService;
    private RpcDBService rpcDBService;
    private AtomicInteger requestIdIncr = new AtomicInteger();
    private int maxSpinDelayConnectCount = SystemPropertyUtil.getInt("netty.nrpc.maxSpinDelayConnectCount",0);

    /**
     * Connection status
     */
    private volatile State state = State.DOWN;
    /**
     * Automatic reconnect Future
     */
    private ScheduledFuture<?> autoReconnectScheduledFuture;

    protected final DataCodec dataCodec;
    protected final Map<Integer, RpcDone> rpcDoneMap;
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

        Map<Integer,RpcDone> futureMap;
        try {
            futureMap = INT_OBJECT_MAP_CONSTRUCTOR != null?
                    INT_OBJECT_MAP_CONSTRUCTOR.newInstance(64):
                    new HashMap<>(64);
        } catch (Exception e) {
            futureMap = new HashMap<>(64);
        }
        this.rpcDoneMap = futureMap;
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

    public void setIdleTime(int idleTime) {
        this.idleTime = idleTime;
    }

    /**
     * Enable automatic reconnection
     */
    public void enableAutoReconnect(){
        enableAutoReconnect(20,TimeUnit.SECONDS,null,true);
    }

    /**
     * Enable automatic reconnection
     * @param heartIntervalSecond The interval at which heartbeat tasks are placed on the queue
     * @param timeUnit Unit of time
     * @param reconnectSuccessHandler Callback method after successful reconnect
     * @param isLogHeartEvent Whether heartbeat event logging is enabled
     */
    public void enableAutoReconnect(int heartIntervalSecond, TimeUnit timeUnit, Consumer<RpcClient> reconnectSuccessHandler, boolean isLogHeartEvent){
        autoReconnectScheduledFuture = RpcClientHeartbeatTask.schedule(heartIntervalSecond,timeUnit,reconnectSuccessHandler,this,isLogHeartEvent);
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

        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, Protocol.RpcService.class);
        if (rpcInterfaceAnn != null) {
            timeout = rpcInterfaceAnn.timeout();
            requestMappingName = rpcInterfaceAnn.value();
        }
        if(requestMappingName.isEmpty()){
            requestMappingName = "/"+StringUtil.firstLowerCase(clazz.getSimpleName());
        }
        return newInstance(clazz,timeout,requestMappingName);
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param requestMappingName requestMappingName
     * @param <T> type
     * @return Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz,int timeout,String requestMappingName){
        return newInstance(clazz,timeout,requestMappingName, new AnnotationMethodToParameterNamesFunction(Arrays.asList(Protocol.RpcParam.class)));
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param requestMappingName requestMappingName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param <T> type
     * @return Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz, int timeout, String requestMappingName, Function<Method,String[]> methodToParameterNamesFunction){
        InvocationHandler rpcInstance = newRpcInstance(clazz,timeout,requestMappingName,methodToParameterNamesFunction);
        Object instance = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, rpcInstance);
        return (T) instance;
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param requestMappingName requestMappingName
     * @param  methodToParameterNamesFunction Method to a function with a parameter name
     * @return Interface implementation class
     */
    public InvocationHandler newRpcInstance(Class clazz, int timeout, String requestMappingName, Function<Method,String[]> methodToParameterNamesFunction){
        Map<String, RpcMethod<RpcClient>> rpcMethodMap = RpcMethod.getMethodMap(this,clazz, methodToParameterNamesFunction);
        if (rpcMethodMap.isEmpty()) {
            throw new IllegalStateException("The RPC service interface must have at least one method, class=[" + clazz.getSimpleName() + "]");
        }
        Sender rpcInstance = new Sender(timeout, requestMappingName,rpcMethodMap);
        rpcInstanceMap.put(requestMappingName,rpcInstance);
        return rpcInstance;
    }

    /**
     * Gets the implementation class
     * @param requestMappingName requestMappingName
     * @return RpcClientInstance
     */
    public InvocationHandler getRpcInstance(String requestMappingName) {
        return rpcInstanceMap.get(requestMappingName);
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

                pipeline.addLast(new IdleStateHandler(idleTime, 0, 0));
                pipeline.addLast(new RpcEncoder());
                pipeline.addLast(new RpcDecoder());
                pipeline.addLast(new ReceiverChannelHandler());
            }
        };
    }

    @Override
    public SocketChannel getChannel() throws RpcConnectException{
        SocketChannel socketChannel = super.getChannel();
        if(socketChannel == null){
            connect();
            spinDelayTryGetConnect(maxSpinDelayConnectCount);
            if(state == State.UP) {
                socketChannel = super.getChannel();
            }else {
                throw new RpcConnectException("The [" + getRemoteAddress() + "] channel no connect");
            }
        }
        if(!socketChannel.isActive()){
            setChannel(null);
            connect();
            spinDelayTryGetConnect(maxSpinDelayConnectCount);
            if(state == State.UP){
                socketChannel = super.getChannel();
            }else {
                throw new RpcConnectException("The [" + getRemoteAddress() + "] channel no active");
            }
        }
        if(!socketChannel.isWritable()){
            throw new RpcConnectException("The ["+getRemoteAddress()+"] channel no writable");
        }
        return socketChannel;
    }

    protected int spinDelayTryGetConnect(int maxCount){
        int count = 0;
        while (connectIngFlag.get()){
            if(count >= maxCount){
                break;
            }
            Thread.yield();
            count++;
        }
        return count;
    }

    @Override
    public void setChannel(SocketChannel newChannel) {
        if(newChannel == null){
            SocketChannel oldChannel = super.getChannel();
            if(oldChannel != null){
                oldChannel.close();
            }
            state = State.DOWN;
        }else {
            super.setChannel(newChannel);
            //The first initiative to send a package to ensure proper binding agreement
            getRpcCommandAsyncService().ping().subscribe(new Subscriber<byte[]>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(1);
                }

                @Override
                public void onNext(byte[] bytes) {
                    state = State.UP;
                }

                @Override
                public void onError(Throwable t) {
                    state = State.DOWN;
                }

                @Override
                public void onComplete() {

                }
            });
        }
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
        if(future.isSuccess()){
            state = State.UP;
        }else {
            state = State.DOWN;
        }
        for (RpcClientAop aop : nettyRpcClientAopList) {
            aop.onConnectAfter(this);
        }
    }

    @Override
    protected void stopAfter(ChannelFuture future) {
        state = State.DOWN;
        rpcInstanceMap.clear();
        rpcCommandService = null;
        rpcDBService = null;

        if(autoReconnectScheduledFuture != null){
            autoReconnectScheduledFuture.cancel(false);
        }
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
        if(rpcCommandAsyncService == null){
            synchronized (this) {
                if(rpcCommandAsyncService == null) {
                    rpcCommandAsyncService = newInstance(RpcCommandAsyncService.class);
                }
            }
        }
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
            RpcPacket packet = new RpcPacket(RpcPacket.TYPE_PING);
            packet.setAck(RpcPacket.ACK_YES);
            ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    class Sender implements InvocationHandler {
        private int timeout;
        private String requestMappingName;
        private Map<String, RpcMethod<RpcClient>> rpcMethodMap;

        Sender(int timeout, String requestMappingName, Map<String, RpcMethod<RpcClient>> rpcMethodMap) {
            this.rpcMethodMap = rpcMethodMap;
            this.timeout = timeout;
            this.requestMappingName = requestMappingName;
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

            RpcMethod<RpcClient> rpcMethod = rpcMethodMap.get(methodName);
            if (rpcMethod == null) {
                return null;
            }

            Object result;
            if(rpcMethod.isReturnTypeReactivePublisherFlag()){
                RpcContext<RpcClient> rpcContext = new RpcContext<>();
                rpcContext.setArgs(args);
                rpcContext.setRpcMethod(rpcMethod);
                result = new RpcClientReactivePublisher(rpcContext,requestMappingName);
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

        Object requestSync(RpcContext<RpcClient> rpcContext) throws Throwable {
            Method method = rpcContext.getRpcMethod().getMethod();
            Class<?> returnType = method.getReturnType();
            byte ackFlag = returnType == void.class? ACK_NO : ACK_YES;

            int requestId = newRequestId();
            RequestPacket rpcRequest = RequestPacket.newInstance();
            rpcRequest.setRequestId(requestId);
            rpcRequest.setRequestMappingName(requestMappingName);
            rpcRequest.setMethodName(method.getName());
            rpcRequest.setAck(ackFlag);

            rpcContext.setRequest(rpcRequest);
            rpcContext.setState(INIT);
            onStateUpdate(rpcContext);

            rpcRequest.setData(dataCodec.encodeRequestData(rpcContext.getArgs(), rpcContext.getRpcMethod()));
            rpcContext.setState(WRITE_ING);
            onStateUpdate(rpcContext);

            RpcClientFuture future = RpcClientFuture.newInstance();
            if (ackFlag == ACK_YES) {
                rpcDoneMap.put(requestId, future);
            }
            try {
                SocketChannel channel = getChannel();
                rpcContext.setRemoteAddress(channel.remoteAddress());
                rpcContext.setLocalAddress(channel.localAddress());
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
                            onStateUpdate(rpcContext);
                        } else {
                            channelFuture.channel().close().addListener(f -> connect());
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
                if (ackFlag == ACK_YES) {
                    rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
                    rpcContext.setResponse(rpcResponse);
                    rpcContext.setState(READ_ING);
                    onStateUpdate(rpcContext);

                    //If the server is not encoded, return directly
                    if (rpcResponse.getEncode() == BINARY) {
                        result = rpcResponse.getData();
                    } else {
                        result = dataCodec.decodeResponseData(rpcResponse.getData(), rpcContext.getRpcMethod());
                    }
                    rpcContext.setResult(result);
                    rpcContext.setState(READ_FINISH);
                    onStateUpdate(rpcContext);
                }
            }catch (Throwable e){
                rpcContext.setThrowable(e);
                throw e;
            }finally {
                rpcDoneMap.remove(requestId);
                try {
                    for (RpcClientAop aop : nettyRpcClientAopList) {
                        aop.onResponseAfter(rpcContext);
                    }
                }finally {
                    RecyclableUtil.release(rpcResponse);
                    future.recycle();
                    rpcContext.recycle();
                }
            }
            return result;
        }
    }

    private static final Constructor<Map> INT_OBJECT_MAP_CONSTRUCTOR;
    static {
        Constructor<Map> intObjectHashMapConstructor;
        try {
            intObjectHashMapConstructor = (Constructor<Map>) Class.forName("io.netty.util.collection.IntObjectHashMap").getConstructor(int.class);;
        } catch (Exception e) {
            intObjectHashMapConstructor = null;
        }
        INT_OBJECT_MAP_CONSTRUCTOR = intObjectHashMapConstructor;
    }

    public static long getTotalInvokeCount() {
        return RpcClientFuture.TOTAL_COUNT.sum();
    }

    public static long getTotalTimeoutCount() {
        return RpcClientFuture.TOTAL_COUNT.sum() - RpcClientFuture.TOTAL_SUCCESS_COUNT.sum();
    }
}
