package com.github.netty.core;

import com.github.netty.core.util.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个抽象的netty客户端
 *
 * @author acer01
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
    private SocketChannels socketChannels;
    private int socketChannelCount = 6;
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
        this.enableEpoll = HostUtil.isLinux() && Epoll.isAvailable();
        this.remoteAddress = remoteAddress;
        this.name = NamespaceUtil.newIdName(namePre,getClass());
    }

    public void setSocketChannelCount(int socketChannelCount) {
        this.socketChannelCount = socketChannelCount <=0? 6: socketChannelCount;
    }

    public void setIoRatio(int ioRatio) {
        if(worker instanceof NioEventLoopGroup){
            ((NioEventLoopGroup) worker).setIoRatio(ioRatio);
            this.ioRatio = ioRatio;
        }else if(worker instanceof EpollEventLoopGroup){
            ((EpollEventLoopGroup) worker).setIoRatio(ioRatio);
            this.ioRatio = ioRatio;
        }
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
            EpollEventLoopGroup epollWorker = new EpollEventLoopGroup(ioThreadCount);
            epollWorker.setIoRatio(ioRatio);
            worker = epollWorker;
        }else {
            NioEventLoopGroup nioWorker = new NioEventLoopGroup(ioThreadCount,new ThreadFactoryX("Client-Worker", NioEventLoopGroup.class));
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

                    //禁用Nagle算法，即数据包立即发送出去 (在TCP_NODELAY模式下，假设有3个小包要发送，第一个小包发出后，接下来的小包需要等待之前的小包被ack，在这期间小包会合并，直到接收到之前包的ack后才会发生)
                    .option(ChannelOption.TCP_NODELAY, true)
                    //开启TCP/IP协议实现的心跳机制
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    //netty的默认内存分配器
                    .option(ChannelOption.ALLOCATOR, ByteBufAllocatorX.INSTANCE);
//                    .option(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT);

            connect();
            this.running = true;
            startAfter();
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage());
//            ExceptionUtil.printRootCauseStackTrace(throwable);
        }
    }

    public boolean isConnect(){
        if(socketChannels == null){
            return false;
        }

        try {
            SocketChannel socketChannel = socketChannels.next();
            if(socketChannel == null){
                return false;
            }
            ChannelFuture future = socketChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).sync();
            return future.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean connect(){
        try {
            synchronized (connectLock) {
                int connectCount = this.socketChannelCount;
                SocketChannel[] socketChannels = new SocketChannel[connectCount];

                for (int i = 0; i < connectCount; i++) {
                    ChannelFuture channelFuture = bootstrap.connect().sync();
                    if (!channelFuture.isSuccess()) {
                        for (int j = 0; j < i; j++) {
                            socketChannels[j].close();
                        }
                        return false;
                    }
                    socketChannels[i] = (SocketChannel) channelFuture.channel();
                }


                if(this.socketChannels != null) {
                    this.socketChannels.close();
                }
                this.socketChannels = new SocketChannels(socketChannels);
                return true;
            }
        } catch (Exception e) {
            Throwable root = ExceptionUtil.getRootCauseNotNull(e);
            logger.error("Connect fail "+remoteAddress +"  : ["+ root.toString()+"]");
            return false;
        }
    }

    public SocketChannel getSocketChannel() {
        if(socketChannels == null){
            return null;
        }
        return socketChannels.next();
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void stop() {
        Throwable cause = null;
        try {
            if(socketChannels != null) {
                socketChannels.close();
                socketChannels = null;
            }
        } catch (Exception e) {
            cause = e;
        }

        stopAfter(cause);
        this.bootstrap = null;
        this.worker = null;
        this.channelFactory = null;
        this.initializerChannelHandler = null;
        this.running = false;
    }

    protected void stopAfter(Throwable cause){
        //有异常抛出
        if(cause != null){
            ExceptionUtil.printRootCauseStackTrace(cause);
        }

        logger.info(name + " stop [remoteAddress = "+remoteAddress+"]...");
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return remoteAddress.getPort();
    }

    protected void startAfter(){
        logger.info(name + " start [activeSocketConnectCount = "+ getActiveSocketChannelCount()+", remoteAddress = "+remoteAddress+"]...");
    }

    public int getActiveSocketChannelCount(){
        return socketChannels == null? 0 : socketChannels.getSocketChannelCount();
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
