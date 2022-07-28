package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.NettyThreadPoolExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class HttpLazyThreadPool implements Supplier<Executor> {
    private final String poolName;
    private /*volatile*/ NettyThreadPoolExecutor executor;

    public HttpLazyThreadPool(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public NettyThreadPoolExecutor get() {
        if (executor == null) {
            synchronized (this) {
                if (executor == null) {
                    int coreThreads = 2;
                    int maxThreads = 200;
                    int keepAliveSeconds = 180;
                    int priority = Thread.NORM_PRIORITY;
                    boolean daemon = false;
                    RejectedExecutionHandler handler = new HttpAbortPolicyWithReport(poolName, System.getProperty("user.home"), "Http Servlet");
                    executor = new NettyThreadPoolExecutor(
                            coreThreads, maxThreads, keepAliveSeconds, TimeUnit.SECONDS,
                            new SynchronousQueue<>(), poolName, priority, daemon, handler);
                }
            }
        }
        return executor;
    }
}