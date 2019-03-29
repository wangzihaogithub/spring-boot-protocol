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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *  An abstract netty client
 * @author wangzihao
 *  2018/8/18/018
 */
public abstract class AbstractNettyClient implements Runnable{

    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final String name;
    private Bootstrap bootstrap;

    private EventLoopGroup worker;
    private ChannelFactory<?extends Channel> channelFactory;
    private ChannelInitializer<?extends Channel> initializerChannelHandler;
    private InetSocketAddress remoteAddress;
    private boolean enableEpoll;
    private SocketChannel channel;
    private final Object connectLock = new Object();
    private int ioThreadCount = 0;
    private int ioRatio = 100;
    private boolean running = false;

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
        this.name = NamespaceUtil.newIdName(namePre,getClass());
    }

    public void setIoRatio(int ioRatio) {
        if(worker instanceof NioEventLoopGroup){
            ((NioEventLoopGroup) worker).setIoRatio(ioRatio);
        }else if(worker instanceof EpollEventLoopGroup){
            ((EpollEventLoopGroup) worker).setIoRatio(ioRatio);
        }
        this.ioRatio = ioRatio;
    }

    public void setIoThreadCount(int ioThreadCount) {
        this.ioThreadCount = ioThreadCount;
    }

    protected abstract ChannelInitializer<?extends Channel> newInitializerChannelHandler();

    protected Bootstrap newClientBootstrap(){
        return new Bootstrap();
    }

    protected EventLoopGroup newWorkerEventLoopGroup() {
        EventLoopGroup worker;
        if(enableEpoll){
            EpollEventLoopGroup epollWorker = new EpollEventLoopGroup(ioThreadCount,new ThreadFactoryX("Epoll","Client-Worker"));
            epollWorker.setIoRatio(ioRatio);
            worker = epollWorker;
        }else {
            NioEventLoopGroup nioWorker = new NioEventLoopGroup(ioThreadCount,new ThreadFactoryX("NIO","Client-Worker"));
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

    @Override
    public final void run() {
        try {
            if(running){
                return;
            }

            this.bootstrap = newClientBootstrap();
            this.worker = newWorkerEventLoopGroup();
            this.channelFactory = newClientChannelFactory();
            this.initializerChannelHandler = newInitializerChannelHandler();

            bootstrap
                    .group(worker)
                    .channelFactory(channelFactory)
                    .handler(initializerChannelHandler)
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
//                    .option(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT);

            connect(this::connectAfter);
            this.running = true;
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage());
//            ExceptionUtil.printRootCauseStackTrace(throwable);
        }
    }

    public boolean isConnect(){
        return getActiveSocketChannelCount() > 0;
    }

    public ChannelFuture connect(Consumer<ChannelFuture> consumer){
        synchronized (connectLock) {
            return bootstrap.connect()
                    .addListener((ChannelFutureListener) future -> {
                if(future.isSuccess()){
                    AbstractNettyClient.this.channel = (SocketChannel) future.channel();
                }else {
                    future.channel().close();
                }
                if(consumer != null) {
                    consumer.accept(future);
                }
            });
        }
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void stop() {
        if(channel != null) {
            channel.close().addListener((ChannelFutureListener) future -> {
                AbstractNettyClient.this.bootstrap = null;
                AbstractNettyClient.this.worker = null;
                AbstractNettyClient.this.channelFactory = null;
                AbstractNettyClient.this.initializerChannelHandler = null;
                AbstractNettyClient.this.running = false;
                stopAfter(future);
            });
            channel = null;
        }
    }

    protected void stopAfter(ChannelFuture future){
        //有异常抛出
        if(future.cause() != null){
            future.cause().printStackTrace();
        }

        logger.info(name + " stop [remoteAddress = "+remoteAddress+"]...");
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return remoteAddress.getPort();
    }

    protected void connectAfter(ChannelFuture future){
        logger.info(name + " connect [activeSocketConnectCount = "+ getActiveSocketChannelCount()+", remoteAddress = "+remoteAddress+"]...");
    }

    public int getActiveSocketChannelCount(){
        return channel != null && channel.isActive()? 1 : 0;
    }

    @Override
    public String toString() {
        return name + "{" +
                "activeSocketChannelCount=" + getActiveSocketChannelCount() +
                ", remoteAddress=" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "}";
    }

    private class SocketChannels {
        private AtomicInteger idx = new AtomicInteger();
        private final SocketChannel[] socketChannels;
        private boolean isPowerOfTwo;
        private volatile boolean close;

        private SocketChannels(SocketChannel[] socketChannels) {
            assert socketChannels != null;
            int count = socketChannels.length;
            isPowerOfTwo = (count & -count) == count;
            this.socketChannels = socketChannels;
        }

        public SocketChannel next() {
            if(close){
                return null;
            }
            int count = socketChannels.length;

            if(count == 1){
                return socketChannels[0];
            }

            if(isPowerOfTwo) {
                return socketChannels[idx.getAndIncrement() & count - 1];
            }else {
                return socketChannels[Math.abs(idx.getAndIncrement() % count)];
            }
        }

        public int getSocketChannelCount() {
            return socketChannels.length;
        }

        public void close(){
            synchronized (socketChannels) {
                close = true;
                int len = socketChannels.length;
                for (int i = 0; i < len; i++) {
                    try {
                        socketChannels[i].close();
                    } catch (Throwable t) {
                        logger.error("SocketChannel close exception : [" + t.toString() + ":" + t.getMessage() + "]");
                    }
                }
                idx = null;
            }
        }
    }

}
