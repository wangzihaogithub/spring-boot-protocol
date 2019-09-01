package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.util.AnnotationMethodToParameterNamesFunction;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.service.RpcCommandService;
import com.github.netty.protocol.nrpc.service.RpcDBService;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcPacket.*;

/**
 * RPC client
 * @author wangzihao
 *  2018/8/18/018
 */
public class RpcClient extends AbstractNettyClient{
    private final Map<String, Sender> rpcInstanceMap = new WeakHashMap<>();
    private int idleTime = 10;
    private RpcCommandService rpcCommandService;
    private RpcDBService rpcDBService;
    private AtomicInteger incr = new AtomicInteger();
    /**
     * Connection status
     */
    private State state;
    /**
     * Automatic reconnect Future
     */
    private ScheduledFuture<?> autoReconnectScheduledFuture;

    protected final DataCodec dataCodec;
    protected final Map<Integer, RpcFuture> futureMap;

    public RpcClient(String remoteHost, int remotePort) {
        this(new InetSocketAddress(remoteHost, remotePort));
    }

    public RpcClient(InetSocketAddress address) {
        this("",address);
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress) {
        super(namePre + Thread.currentThread().getName()+"-", remoteAddress);
        this.dataCodec = new JsonDataCodec();
        Map<Integer,RpcFuture> futureMap;
        try {
            futureMap = intObjectHashMapConstructor != null?
                    intObjectHashMapConstructor.newInstance(64):
                    new HashMap<>(64);
        } catch (Exception e) {
            futureMap = new HashMap<>(64);
        }
        this.futureMap = futureMap;
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
        Map<String, RpcMethod> rpcMethodMap = RpcMethod.getMethodMap(clazz, methodToParameterNamesFunction);
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
    protected ChannelInitializer<? extends Channel> newInitializerChannelHandler() {
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
    public SocketChannel getChannel() {
        SocketChannel socketChannel = super.getChannel();
        if(socketChannel == null){
            connect();
            throw new RpcConnectException("The ["+getRemoteAddress()+"] channel no connect");
        }
        if(!socketChannel.isActive()){
            connect();
            throw new RpcConnectException("The ["+getRemoteAddress()+"] channel no active");
        }
        if(!socketChannel.isWritable()){
            throw new RpcConnectException("The ["+getRemoteAddress()+"] channel no writable");
        }
        return socketChannel;
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
     * Get connection status
     * @return State
     */
    public State getState() {
        return state;
    }

    public int newRequestId(){
        int id = incr.getAndIncrement();
        if(id < 0){
            id = 0;
            incr.set(id);
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


    public class ReceiverChannelHandler extends AbstractChannelHandler<RpcPacket,Object> {
        public ReceiverChannelHandler() {
            super(false);
        }

        @Override
        protected void onMessageReceived(ChannelHandlerContext ctx, RpcPacket packet) throws Exception {
            if(packet instanceof ResponsePacket) {
                ResponsePacket rpcResponse = (ResponsePacket) packet;

                RpcFuture future = futureMap.remove(rpcResponse.getRequestId());
                if (future != null) {
                    //Handed over to the thread that sent the message
                    future.done(rpcResponse);
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

    public class Sender implements InvocationHandler {
        private int timeout;
        private String requestMappingName;
        private Map<String, RpcMethod> rpcMethodMap;

        Sender(int timeout, String requestMappingName, Map<String, RpcMethod> rpcMethodMap) {
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
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            if ("toString".equals(methodName) && parameterTypes.length == 0) {
                return this.toString();
            }
            if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
                return this.hashCode();
            }
            if ("equals".equals(methodName) && parameterTypes.length == 1) {
                return this.equals(args[0]);
            }

            RpcMethod rpcMethod = rpcMethodMap.get(methodName);
            if (rpcMethod == null) {
                return null;
            }

            int requestId = newRequestId();

            RequestPacket rpcRequest = RequestPacket.newInstance();
            rpcRequest.setRequestId(requestId);
            rpcRequest.setRequestMappingName(requestMappingName);
            rpcRequest.setMethodName(rpcMethod.getMethod().getName());
            rpcRequest.setData(dataCodec.encodeRequestData(args, rpcMethod));
            rpcRequest.setAck(ACK_YES);

            RpcFuture future = RpcFuture.newInstance();
            futureMap.put(requestId, future);

            getChannel().writeAndFlush(rpcRequest).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

            ResponsePacket rpcResponse = null;
            try {
                rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
                futureMap.remove(requestId);
                //If the server is not encoded, return directly
                if (rpcResponse.getEncode() == BINARY) {
                    return rpcResponse.getData();
                } else {
                    return dataCodec.decodeResponseData(rpcResponse.getData());
                }
            } finally {
                RecyclableUtil.release(rpcResponse);
                future.recycle();
            }
        }
    }

    private static Constructor<Map> intObjectHashMapConstructor;
    static {
        try {
            intObjectHashMapConstructor = (Constructor<Map>) Class.forName("io.netty.util.collection.IntObjectHashMap").getConstructor(int.class);;
        } catch (Exception e) {
            intObjectHashMapConstructor = null;
        }
    }

    public static long getTotalInvokeCount() {
        return RpcFuture.TOTAL_COUNT.get();
    }

    public static long getTotalTimeoutCount() {
        return RpcFuture.TOTAL_COUNT.get() - RpcFuture.TOTAL_SUCCESS_COUNT.get();
    }
}
