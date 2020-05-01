package com.github.netty.core;

import com.github.netty.core.util.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *  An abstract netty client
 * @author wangzihao
 *  2018/8/18/018
 */
public abstract class AbstractNettyClient{
    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final String name;
    private final String namePre;
    private Bootstrap bootstrap;

    private EventLoopGroup worker;
    protected InetSocketAddress remoteAddress;
    private boolean enableEpoll;
    private volatile SocketChannel channel;
    protected final AtomicBoolean connectIngFlag = new AtomicBoolean(false);
    private int ioThreadCount = 0;
    private int ioRatio = 100;
    private AtomicBoolean initFlag = new AtomicBoolean(false);

    public AbstractNettyClient() {
        this("",null);
    }

    public AbstractNettyClient(String remoteHost,int remotePort) {
        this(new InetSocketAddress(remoteHost,remotePort));
    }

    public AbstractNettyClient(InetSocketAddress remoteAddress) {
        this("",remoteAddress);
    }

    /**
     *
     * @param namePre 名称前缀
     * @param remoteAddress 远程地址
     */
    public AbstractNettyClient(String namePre,InetSocketAddress remoteAddress) {
        this.enableEpoll = Epoll.isAvailable();
        this.remoteAddress = remoteAddress;
        this.namePre = namePre;
        this.name = NamespaceUtil.newIdName(namePre,getClass());
        if(enableEpoll) {
            logger.info("enable epoll client = {}",this);
        }
    }

    public void setIoRatio(int ioRatio) {
        if(worker instanceof NioEventLoopGroup){
            ((NioEventLoopGroup) worker).setIoRatio(ioRatio);
        }else if(worker instanceof EpollEventLoopGroup){
//            ((EpollEventLoopGroup) worker).setIoRatio(ioRatio);
        }
        this.ioRatio = ioRatio;
    }

    public void setIoThreadCount(int ioThreadCount) {
        this.ioThreadCount = ioThreadCount;
    }

    protected abstract ChannelHandler newBossChannelHandler();

    protected Bootstrap newClientBootstrap(){
        return new Bootstrap();
    }

    protected EventLoopGroup newWorkerEventLoopGroup() {
        EventLoopGroup worker;
        if(enableEpoll){
            EpollEventLoopGroup epollWorker = new EpollEventLoopGroup(ioThreadCount,new ThreadFactoryX("Epoll",namePre+"Client-Worker"));
//            epollWorker.setIoRatio(ioRatio);
            worker = epollWorker;
        }else {
            NioEventLoopGroup nioWorker = new NioEventLoopGroup(ioThreadCount,new ThreadFactoryX("NIO",namePre+"Client-Worker"));
            nioWorker.setIoRatio(ioRatio);
            worker = nioWorker;
        }
        return worker;
    }

    protected ChannelFactory<? extends Channel> newClientChannelFactory() {
        ChannelFactory<? extends Channel> channelFactory;
        if(enableEpoll){
            channelFactory = EpollSocketChannel::new;
        }else {
            channelFactory = NioSocketChannel::new;
        }
        return channelFactory;
    }

    protected AbstractNettyClient init() {
        this.bootstrap = newClientBootstrap();
        this.worker = newWorkerEventLoopGroup();
        ChannelFactory<?extends Channel> channelFactory = newClientChannelFactory();
        ChannelHandler bossChannelHandler = newBossChannelHandler();

        this.bootstrap
                .group(worker)
                .channelFactory(channelFactory)
                .handler(bossChannelHandler)
                .remoteAddress(remoteAddress)
                //用于构造服务端套接字ServerSocket对象，标识当服务器请求处理线程全满时，用于临时存放已完成三次握手的请求的队列的最大长度
//                    .option(ChannelOption.SO_BACKLOG, 1024) // determining the number of connections queued
                //netty boos的默认内存分配器
//                    .option(ChannelOption.ALLOCATOR, ByteBufAllocatorX.INSTANCE)
                //禁用Nagle算法，即数据包立即发送出去 (在TCP_NODELAY模式下，假设有3个小包要发送，第一个小包发出后，接下来的小包需要等待之前的小包被ack，在这期间小包会合并，直到接收到之前包的ack后才会发生)
                .option(ChannelOption.TCP_NODELAY, true)
                //开启TCP/IP协议实现的心跳机制
                .option(ChannelOption.SO_KEEPALIVE, true)
                //netty的默认内存分配器
                .option(ChannelOption.ALLOCATOR, ByteBufAllocatorX.INSTANCE);
        return this;
    }

    public AbstractNettyClient config(Bootstrap bootstrap){
        return this;
    }

    public boolean isConnect(){
        return getActiveSocketChannelCount() > 0;
    }

    public Optional<ChannelFuture> connect(){
        return connect(remoteAddress);
    }

    public Optional<ChannelFuture> connect(InetSocketAddress remoteAddress){
        if(connectIngFlag.compareAndSet(false,true)) {
            if(initFlag.compareAndSet(false,true)) {
                init();
            }
            this.remoteAddress = remoteAddress == null? (InetSocketAddress) bootstrap.config().remoteAddress() : remoteAddress;
            return Optional.of(bootstrap.connect(this.remoteAddress)
                    .addListener((ChannelFutureListener) future -> {
                        try {
                            if (future.isSuccess()) {
                                setChannel((SocketChannel) future.channel());
                            } else {
                                future.channel().close();
                            }
                        }finally {
                            connectIngFlag.set(false);
                        }
                        connectAfter(future);
                    }));
        }
        return Optional.empty();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void setChannel(SocketChannel channel) {
        this.channel = channel;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public EventLoopGroup getWorker() {
        return worker;
    }

    public int getIoRatio() {
        return ioRatio;
    }

    public int getIoThreadCount() {
        return ioThreadCount;
    }

    public boolean isConnectIng() {
        return connectIngFlag.get();
    }

    public boolean isEnableEpoll() {
        return enableEpoll;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public ChannelFuture stop() {
        if(channel == null) {
            throw new IllegalStateException("channel is null");
        }
        return channel.close().addListener((ChannelFutureListener) future -> {
            AbstractNettyClient.this.bootstrap = null;
            AbstractNettyClient.this.worker.shutdownGracefully();
            AbstractNettyClient.this.worker= null;
            AbstractNettyClient.this.initFlag.set(false);
            AbstractNettyClient.this.channel = null;
            stopAfter(future);
        });
    }

    protected void stopAfter(ChannelFuture future){
        //有异常抛出
        if(future.cause() != null){
            logger.error("stopAfter. error={}",future.cause(),future.cause());
        }
        logger.info("{} stop [remoteAddress = {}]...",getName(),getRemoteAddress());
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return remoteAddress.getPort();
    }

    protected void connectAfter(ChannelFuture future){
        logger.info("{} connect [activeSocketConnectCount = {}, remoteAddress = {}]...",
                getName(),getActiveSocketChannelCount(),getRemoteAddress());
    }

    public int getActiveSocketChannelCount(){
        return channel != null && channel.isActive()? 1 : 0;
    }

    @Override
    public String toString() {
        return name + "{" +
                "channel=" + channel +
                ", remoteAddress=" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "}";
    }

}
