package com.github.netty.protocol.servlet.http2;

import com.github.netty.core.util.LoggerFactoryX;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.PlatformDependent;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * http2 连接池 (因为http2是长连接)
 *
 * @author wangzihaogithub 2021年2月3日15:43:40
 */
public class NettyHttp2ClientPool extends ConcurrentHashMap<String, List<NettyHttp2Client>> implements Closeable {
    private int connectTimeout = 5000;
    private int workerCount = 0;
    private int clientCount = 2;
    private volatile EventLoopGroup worker;

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public NettyHttp2Client getIfCreate(URL url) {
        String cacheKey = url.getProtocol() + "//" + url.getHost() + ":" + url.getPort();
        List<NettyHttp2Client> clients = computeIfAbsent(cacheKey, new Function<String, List<NettyHttp2Client>>() {
            @Override
            public List<NettyHttp2Client> apply(String s) {
                if (worker == null) {
                    synchronized (this) {
                        if (worker == null) {
                            worker = new NioEventLoopGroup(Math.max(workerCount, 4));
                        }
                    }
                }
                List<NettyHttp2Client> list = new ChooserList<>();
                for (int i = 0; i < clientCount; i++) {
                    try {
                        list.add(new NettyHttp2Client(url, worker)
                                .connectTimeout(connectTimeout));
                    } catch (UnknownHostException | SSLException e) {
                        PlatformDependent.throwException(e);
                    }
                }
                return list;
            }
        });
        NettyHttp2Client client = ((ChooserList<NettyHttp2Client>) clients).next();
        return client;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getClientCount() {
        return clientCount;
    }

    public void setClientCount(int clientCount) {
        this.clientCount = clientCount;
    }

    @Override
    public void close() {
        for (List<NettyHttp2Client> clients : values()) {
            for (NettyHttp2Client client : clients) {
                client.close().addListener(new GenericFutureListener<Future<? super Long>>() {
                    @Override
                    public void operationComplete(Future<? super Long> future) throws Exception {
                        LoggerFactoryX.getLogger(NettyHttp2ClientPool.class)
                                .info("http2 client close = {}", client.getRemoteAddress());
                    }
                });
            }
        }
        clear();
        EventLoopGroup worker = this.worker;
        if (worker != null && !worker.isShutdown()) {
            worker.shutdownGracefully();
        }
    }

    static class ChooserList<T> extends ArrayList<T> {
        private int i = 0;

        public T next() {
            return get(Math.abs(i++ % size()));
        }
    }
}
