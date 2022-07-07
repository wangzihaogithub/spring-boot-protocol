package com.github.netty.core.util;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author wangzihao
 */
public class ThreadPoolX extends ScheduledThreadPoolExecutor {

    private static ThreadPoolX DEFAULT_INSTANCE;

    public static ThreadPoolX getDefaultInstance(){
        if(DEFAULT_INSTANCE == null){
            synchronized (ThreadPoolX.class){
                if(DEFAULT_INSTANCE == null){
                    DEFAULT_INSTANCE = new ThreadPoolX("Default", SystemPropertyUtil.getInt("netty-core.defaultThreadPoolCount",3));
                }
            }
        }
        return DEFAULT_INSTANCE;
    }

    public ThreadPoolX(int corePoolSize) {
        this("",corePoolSize);
    }

    public ThreadPoolX(String preName, int corePoolSize) {
        this(preName,corePoolSize,Thread.MAX_PRIORITY,false);
    }

    public ThreadPoolX(String preName, int corePoolSize, int priority,boolean daemon) {
        super(corePoolSize, new ThreadFactoryX(preName, ThreadPoolX.class,priority,daemon), new RejectedExecutionHandlerX());
    }

    @Override
    public void execute(Runnable command) {
        super.execute(command);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        //
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        //
    }

    private static class RejectedExecutionHandlerX implements RejectedExecutionHandler {

        private RejectedExecutionHandlerX() { }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        }
    }

}
