package com.github.netty.register.rpc;

import com.github.netty.annotation.RegisterFor;
import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.util.AnnotationMethodToParameterNamesFunction;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import com.github.netty.register.rpc.exception.RpcConnectException;
import com.github.netty.register.rpc.exception.RpcException;
import com.github.netty.register.rpc.service.RpcCommandService;
import com.github.netty.register.rpc.service.RpcDBService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * RPC 客户端
 * @author acer01
 *  2018/8/18/018
 */
public class RpcClient extends AbstractNettyClient{

    /**
     * rpc客户端处理器
     */
    private RpcClientChannelHandler rpcClientHandler = new RpcClientChannelHandler(this::getSocketChannel);
    private RpcEncoder rpcEncoder = new RpcEncoder(RpcRequest.class);
    private Supplier rpcResponseSupplier = RpcResponse::new;
    /**
     * 实例
     */
    private final Map<String,RpcClientInstance> rpcInstanceMap = new WeakHashMap<>();
    /**
     * rpc命令服务
     */
    private RpcCommandService rpcCommandService;
    /**
     * rpc数据服务
     */
    private RpcDBService rpcDBService;
    /**
     * 连接状态
     */
    private State state;
    /**
     * 创建客户端的线程
     */
    private Thread thread;
    /**
     * 自动重连Future
     */
    private ScheduledFuture<?> autoReconnectScheduledFuture;

    public RpcClient(String remoteHost, int remotePort) {
        this(new InetSocketAddress(remoteHost, remotePort));
    }

    public RpcClient(InetSocketAddress address) {
        this("",address);
    }

    public RpcClient(String namePre, InetSocketAddress remoteAddress) {
        super(namePre + Thread.currentThread().getName()+"-", remoteAddress);
        this.thread = Thread.currentThread();
    }

    /**
     * 开启自动重连
     */
    public void enableAutoReconnect(){
        enableAutoReconnect(20,TimeUnit.SECONDS,null,true);
    }

    /**
     * 开启自动重连
     * @param heartIntervalSecond 心跳任务放入队列的时间间隔
     * @param timeUnit 时间单位
     * @param reconnectSuccessHandler 重连成功后的回调方法
     * @param isLogHeartEvent 是否开启心跳事件日志
     */
    public void enableAutoReconnect(int heartIntervalSecond, TimeUnit timeUnit, Consumer<RpcClient> reconnectSuccessHandler, boolean isLogHeartEvent){
        autoReconnectScheduledFuture = RpcClientHeartbeatTask.schedule(heartIntervalSecond,timeUnit,reconnectSuccessHandler,this,isLogHeartEvent);
    }

    /**
     * 新建实现类
     * @param clazz 接口 interface
     * @param <T>
     * @return  接口的实现类
     */
    public <T>T newInstance(Class<T> clazz){
        int timeout = RegisterFor.RpcService.DEFAULT_TIME_OUT;
        String serviceName = "";

        RegisterFor.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(clazz, RegisterFor.RpcService.class);
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
     * 新建实现类
     * @param clazz 接口 interface
     * @param timeout 超时时间
     * @param serviceName 服务名称
     * @param <T>
     * @return 接口的实现类
     */
    public <T>T newInstance(Class<T> clazz,int timeout,String serviceName){
        return newInstance(clazz,timeout,serviceName, new AnnotationMethodToParameterNamesFunction(Arrays.asList(RegisterFor.RpcParam.class)));
    }

    /**
     * 新建实现类
     * @param clazz 接口 interface
     * @param timeout 超时时间
     * @param serviceName 服务名称
     * @param methodToParameterNamesFunction 方法转参数名的函数
     * @param <T>
     * @return 接口的实现类
     */
    public <T>T newInstance(Class<T> clazz, int timeout, String serviceName, Function<Method,String[]> methodToParameterNamesFunction){
        RpcClientInstance rpcInstance = rpcClientHandler.newInstance(timeout,serviceName,clazz,methodToParameterNamesFunction);
        rpcInstanceMap.put(serviceName,rpcInstance);
        Object instance = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, rpcInstance);
        return (T) instance;
    }

    /**
     * 新建实现类
     * @param clazz 接口 interface
     * @param timeout 超时时间
     * @param serviceName 服务名称
     * @param  methodToParameterNamesFunction 方法转参数名的函数
     * @return 接口的实现类
     */
    public RpcClientInstance newRpcInstance(Class clazz, int timeout, String serviceName, Function<Method,String[]> methodToParameterNamesFunction){
        RpcClientInstance rpcInstance = rpcClientHandler.newInstance(timeout,serviceName,clazz,methodToParameterNamesFunction);
        rpcInstanceMap.put(serviceName,rpcInstance);
        return rpcInstance;
    }

    /**
     * 获取实现类MAP
     * @return
     */
    public RpcClientInstance getRpcInstance(String serviceName) {
        return rpcInstanceMap.get(serviceName);
    }

    /**
     * 初始化所有处理器
     * @return
     */
    @Override
    protected ChannelInitializer<? extends Channel> newInitializerChannelHandler() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(rpcEncoder);
                pipeline.addLast(new RpcDecoder(rpcResponseSupplier));
                pipeline.addLast(rpcClientHandler);
            }
        };
    }

    /**
     * 获取链接
     * @return
     */
    @Override
    public SocketChannel getSocketChannel() {
        SocketChannel socketChannel = super.getSocketChannel();
        if(socketChannel == null){
            throw new RpcConnectException("The ["+getRemoteAddress()+"] channel no connect");
        }
        return socketChannel;
    }

    @Override
    public boolean isConnect() {
        if(rpcCommandService == null){
            return super.isConnect();
        }

        SocketChannel channel = super.getSocketChannel();
        if(channel == null){
            return false;
        }
        try {
            return rpcCommandService.ping() != null;
        }catch (RpcException e){
            return false;
        }
    }

    @Override
    public boolean connect() {
        boolean success = super.connect();
        if(success){
            state = State.UP;
        }else {
            state = State.DOWN;
        }
        return success;
    }

    @Override
    public void stop() {
        super.stop();
        if(autoReconnectScheduledFuture != null){
            autoReconnectScheduledFuture.cancel(false);
        }
    }

    /**
     * 获取数据服务
     * @return
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
     * 获取命令服务
     * @return
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
     * 获取连接状态
     * @return
     */
    public State getState() {
        return state;
    }

    /**
     * 获取创建客户端的线程
     * @return
     */
    public Thread getThread() {
        return thread;
    }

    /**
     * 客户端连接状态
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

}
