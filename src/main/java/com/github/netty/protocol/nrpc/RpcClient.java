package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.Packet;
import com.github.netty.core.util.*;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.service.RpcCommandService;
import com.github.netty.protocol.nrpc.service.RpcDBService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AsciiString;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.netty.core.Packet.ACK_YES;
import static com.github.netty.core.util.IOUtil.INT_LENGTH;
import static com.github.netty.protocol.nrpc.DataCodec.CHARSET_UTF8;
import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;

/**
 * RPC client
 * @author wangzihao
 *  2018/8/18/018
 */
public class RpcClient extends AbstractNettyClient{
    private final Map<String,RpcClientSender> rpcInstanceMap = new WeakHashMap<>();
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
    protected final Map<Integer, RpcFuture<RpcResponsePacket>> futureMap = new ConcurrentHashMap<>(64);

    public RpcClient(String remoteHost, int remotePort) {
        this(new InetSocketAddress(remoteHost, remotePort));
    }

    public RpcClient(InetSocketAddress address) {
        this("",address);
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress) {
        super(namePre + Thread.currentThread().getName()+"-", remoteAddress);
        this.dataCodec = new JsonDataCodec();
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
        String serviceName = "";

        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, Protocol.RpcService.class);
        if (rpcInterfaceAnn != null) {
            timeout = rpcInterfaceAnn.timeout();
            serviceName = rpcInterfaceAnn.value();
        }
        if(serviceName.isEmpty()){
            serviceName = "/"+StringUtil.firstLowerCase(clazz.getSimpleName());
        }
        return newInstance(clazz,timeout,serviceName);
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param serviceName serviceName
     * @param <T> type
     * @return Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz,int timeout,String serviceName){
        return newInstance(clazz,timeout,serviceName, new AnnotationMethodToParameterNamesFunction(Arrays.asList(Protocol.RpcParam.class)));
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param serviceName serviceName
     * @param methodToParameterNamesFunction Method to a function with a parameter name
     * @param <T> type
     * @return Interface implementation class
     */
    public <T>T newInstance(Class<T> clazz, int timeout, String serviceName, Function<Method,String[]> methodToParameterNamesFunction){
        InvocationHandler rpcInstance = newRpcInstance(clazz,timeout,serviceName,methodToParameterNamesFunction);
        Object instance = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, rpcInstance);
        return (T) instance;
    }

    /**
     * New implementation class
     * @param clazz  interface
     * @param timeout timeout
     * @param serviceName serviceName
     * @param  methodToParameterNamesFunction Method to a function with a parameter name
     * @return Interface implementation class
     */
    public InvocationHandler newRpcInstance(Class clazz, int timeout, String serviceName, Function<Method,String[]> methodToParameterNamesFunction){
        Map<String, RpcMethod> rpcMethodMap = RpcMethod.getMethodMap(clazz, methodToParameterNamesFunction);
        if (rpcMethodMap.isEmpty()) {
            throw new IllegalStateException("The RPC service interface must have at least one method, class=[" + clazz.getSimpleName() + "]");
        }
        RpcClientSender rpcInstance = new RpcClientSender(timeout, serviceName,rpcMethodMap);
        rpcInstanceMap.put(serviceName,rpcInstance);
        return rpcInstance;
    }

    /**
     * Gets the implementation class
     * @param serviceName serviceName
     * @return RpcClientInstance
     */
    public InvocationHandler getRpcInstance(String serviceName) {
        return rpcInstanceMap.get(serviceName);
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

//                pipeline.addLast("idleStateHandler", new IdleStateHandler(idleTime, 0, 0));
                pipeline.addLast(new RpcEncoder());
                pipeline.addLast(new RpcDecoder());
                pipeline.addLast(new RpcClientReceiverChannelHandler());
            }
        };
    }

    @Override
    public SocketChannel getChannel() {
        SocketChannel socketChannel = super.getChannel();
        if(socketChannel == null){
            throw new RpcConnectException("The ["+getRemoteAddress()+"] channel no connect");
        }
        if(!socketChannel.isActive()){
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
    public void stop() {
        super.stop();
        if(autoReconnectScheduledFuture != null){
            autoReconnectScheduledFuture.cancel(false);
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


    public class RpcClientReceiverChannelHandler extends AbstractChannelHandler<Packet,Object> {
        public RpcClientReceiverChannelHandler() {
            super(false);
        }

        @Override
        protected void onMessageReceived(ChannelHandlerContext ctx, Packet packet) throws Exception {
            if(packet.refCnt() <= 0){
                logger.error("onMessageReceived packet.refCnt() <= 0");
            }
            if(packet instanceof RpcResponsePacket) {
                RpcResponsePacket rpcResponse = (RpcResponsePacket) packet;

                RpcFuture<RpcResponsePacket> future = futureMap.remove(rpcResponse.getRequestIdInt());
                if (future != null) {
                    //Handed over to the thread that sent the message
                    future.done(rpcResponse);
                    return;
                }

                ByteBuf byteBuf = rpcResponse.getFieldMap().get(AsciiString.of("time"));
                long c = (System.currentTimeMillis() - byteBuf.getLong(0));

                try {
                    logger.error("time out1 {} ,time={}毫秒  ,Id={}. response={}",
                            c,
                            System.currentTimeMillis() - byteBuf.getLong(0),
                            rpcResponse.getRequestIdInt(),
                            rpcResponse + "");
                    //If the fetch does not indicate that the timeout has occurred, it is released
                    RecyclableUtil.release(rpcResponse);
                }catch (Throwable t){
                    t.printStackTrace();
                }
            }else {
                logger.info("client received packet={}",packet);
                RecyclableUtil.release(packet);
            }
        }

        @Override
        protected void onReaderIdle(ChannelHandlerContext ctx) {
            //heart beat
            Packet packet = new Packet(Packet.TYPE_PING);
            packet.setAck(Packet.ACK_YES);
            ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    public class RpcClientSender implements InvocationHandler {
        private int timeout;
        private byte[] serviceName;
        private Map<String, RpcMethod> rpcMethodMap;
        private byte[] requestIdBytes = new byte[INT_LENGTH];

        public RpcClientSender(int timeout, String serviceName, Map<String, RpcMethod> rpcMethodMap) {
            this.rpcMethodMap = rpcMethodMap;
            this.timeout = timeout;
            this.serviceName = serviceName.getBytes(CHARSET_UTF8);
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
            IOUtil.setInt(requestIdBytes, 0, requestId);

            RpcRequestPacket rpcRequest = new RpcRequestPacket();
            rpcRequest.setRequestId(RecyclableUtil.newReadOnlyBuffer(requestIdBytes));
            rpcRequest.setServiceName(RecyclableUtil.newReadOnlyBuffer(serviceName));
            rpcRequest.setMethodName(RecyclableUtil.newReadOnlyBuffer(rpcMethod.getMethodName()));
            rpcRequest.setBody(dataCodec.encodeRequestData(args, rpcMethod));
            rpcRequest.setAck(ACK_YES);
            rpcRequest.getFieldMap().put(AsciiString.of("time"), Unpooled.copyLong(System.currentTimeMillis()));

            RpcFuture<RpcResponsePacket> future = RpcFuture.newInstance(rpcRequest);
            futureMap.put(requestId, future);

            getChannel().writeAndFlush(rpcRequest).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            RpcResponsePacket rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
            futureMap.remove(requestId);

            try {
                ByteBuf body = rpcResponse.getBody();
                //If the server is not encoded, return directly
                if (rpcResponse.getEncode() == BINARY) {
                    return ByteBufUtil.getBytes(body, body.readerIndex(), body.readableBytes(), false);
                } else {
                    return dataCodec.decodeResponseData(body);
                }
            } finally {
                future.recycle();
                RecyclableUtil.release(rpcResponse);
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

    public static String getTimeoutApis() {
        return String.join(",", RpcFuture.TIMEOUT_API.keySet());
    }

    public static long getTotalInvokeCount() {
        return RpcFuture.TOTAL_INVOKE_COUNT.get();
    }

    public static long getTotalTimeoutCount() {
        return RpcFuture.TIMEOUT_API.values().stream().reduce(0,Integer::sum);
    }
}
