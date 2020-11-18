package com.github.netty.core.util;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.*;

public class NettyThreadPoolExecutor extends ThreadPoolExecutor {

    public NettyThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                   BlockingQueue<Runnable> workQueue, String poolName,int priority,boolean daemon,
                                   RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new DefaultThreadFactory(poolName,daemon,priority,
                System.getSecurityManager() == null ? Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup()){
            @Override
            protected Thread newThread(Runnable r, String name) {
                return new NettyThreadX(threadGroup,r, name);
            }
        }, handler);
    }
}
