package com.github.netty.core.util;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Use netty thread
 *
 * @author wangzihaogithub 2020-11-21
 * @see io.netty.util.concurrent.DefaultThreadFactory
 * @see io.netty.util.concurrent.FastThreadLocalThread
 * @see io.netty.util.internal.InternalThreadLocalMap#handlerSharableCache()
 */
public class NettyThreadPoolExecutor extends ThreadPoolExecutor {

    public NettyThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                   BlockingQueue<Runnable> workQueue, String poolName, int priority, boolean daemon,
                                   RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new DefaultThreadFactory(poolName, daemon, priority,
                System.getSecurityManager() == null ? Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup()) {
            @Override
            protected Thread newThread(Runnable r, String name) {
                return new NettyThreadX(threadGroup, r, name);
            }
        }, handler);
    }
}
