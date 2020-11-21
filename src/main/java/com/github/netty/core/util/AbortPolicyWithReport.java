package com.github.netty.core.util;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;

/**
 * Abort Policy.
 * Log warn info when abort.
 */
public class AbortPolicyWithReport extends ThreadPoolExecutor.AbortPolicy {
    protected final String info;
    protected final String threadName;
    protected final String dumpPath;

    private static volatile long lastPrintTime = 0;

    private static final long TEN_MINUTES_MILLS = 10 * 60 * 1000;

    private static Semaphore guard = new Semaphore(1);

    public AbortPolicyWithReport(String threadName,String dumpPath,String info) {
        this.threadName = threadName;
        this.dumpPath = dumpPath == null? System.getProperty("user.home"): dumpPath;
        this.info = info;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        String msg = String.format("Thread pool is EXHAUSTED!" +
                " Thread Name: %s, Pool Size: %d (active: %d, core: %d, max: %d, largest: %d), Task: %d (completed: "
                + "%d)," +
                " Executor status:(isShutdown:%s, isTerminated:%s, isTerminating:%s), in %s",
            threadName, e.getPoolSize(), e.getActiveCount(), e.getCorePoolSize(), e.getMaximumPoolSize(),
            e.getLargestPoolSize(),
            e.getTaskCount(), e.getCompletedTaskCount(), e.isShutdown(), e.isTerminated(), e.isTerminating(),info);
        LoggerFactoryX.getLogger(AbortPolicyWithReport.class).warn(msg);
        if(!dumpPath.isEmpty()){
            dumpJStack();
        }
        throw new RejectedExecutionException(msg);
    }

    protected void dumpJStack() {
        long now = System.currentTimeMillis();

        //dump every 10 minutes
        if (now - lastPrintTime < TEN_MINUTES_MILLS) {
            return;
        }

        if (!guard.tryAcquire()) {
            return;
        }

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

            String dateStr = sdf.format(new Date());
            //try-with-resources
            try (FileOutputStream jStackStream = new FileOutputStream(
                new File(dumpPath, "NettyX_JStack.log" + "." + dateStr))) {
                JVMUtil.jstack(jStackStream);
            } catch (Throwable t) {
                LoggerFactoryX.getLogger(AbortPolicyWithReport.class).error("dump jStack error", t);
            } finally {
                guard.release();
            }
            lastPrintTime = System.currentTimeMillis();
        });
        //must shutdown thread pool ,if not will lead to OOM
        pool.shutdown();
    }

}
