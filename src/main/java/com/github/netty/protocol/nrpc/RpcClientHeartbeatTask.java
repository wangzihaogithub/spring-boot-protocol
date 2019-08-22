package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.core.util.ThreadPoolX;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 * Heartbeat tasks (including automatic reconnection)
 *
 * @author wangzihao
 */
public class RpcClientHeartbeatTask implements Runnable{
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());

    private RpcClient rpcClient;
    /**
     * Reconnect the successful callback method
     */
    private Consumer<RpcClient> reconnectSuccessHandler;
    /**
     * Reconnection number
     */
    private int reconnectCount;
    /**
     * The number of times it was called
     */
    private AtomicInteger scheduleCount = new AtomicInteger();
    /**
     * Scheduling thread pool
     */
    private static ThreadPoolX SCHEDULE_POOL;
    /**
     * A client object has only one heartbeat task. If a task already exists, the new task will overwrite the old one.
     */
    private static final Map<RpcClient,ScheduledQueueTask> SCHEDULE_MAP = new HashMap<>();
    /**
     * Heartbeat task queue
     */
    private static final BlockingQueue<RpcClientHeartbeatTask> TASK_QUEUE = new LinkedBlockingQueue<>();
    /**
     * Whether it is a daily heartbeat event
     */
    private boolean isLogHeartEvent;

    private RpcClientHeartbeatTask(RpcClient rpcClient, Consumer<RpcClient> reconnectSuccessHandler, boolean isLogHeartEvent) {
        this.rpcClient = rpcClient;
        this.reconnectSuccessHandler = reconnectSuccessHandler;
        this.isLogHeartEvent = isLogHeartEvent;
    }

    /**
     * Assign reconnect tasks
     * @param heartIntervalSecond heartIntervalSecond
     * @param timeUnit timeUnit
     * @param reconnectSuccessHandler reconnectSuccessHandler
     * @param rpcClient rpcClient
     * @param isLogHeartEvent isLogHeartEvent
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> schedule(int heartIntervalSecond, TimeUnit timeUnit, Consumer<RpcClient> reconnectSuccessHandler, RpcClient rpcClient, boolean isLogHeartEvent){
        RpcClientHeartbeatTask heartbeatTask = new RpcClientHeartbeatTask(rpcClient,reconnectSuccessHandler,isLogHeartEvent);

        ScheduledQueueTask oldScheduledQueueTask = SCHEDULE_MAP.get(rpcClient);
        if(oldScheduledQueueTask != null){
            oldScheduledQueueTask.cancel();
        }

        ScheduledQueueTask scheduledQueueTask = new ScheduledQueueTask(heartbeatTask);
        scheduledQueueTask.scheduledFuture = getSchedulePool().scheduleWithFixedDelay(scheduledQueueTask,heartIntervalSecond,heartIntervalSecond,timeUnit);
        SCHEDULE_MAP.put(rpcClient,scheduledQueueTask);

        return scheduledQueueTask.scheduledFuture;
    }

    /**
     * reconnection
     * @param causeMessage Reconnection reason
     */
    private void reconnect(String causeMessage) throws InterruptedException {
        ++reconnectCount;
        Optional<ChannelFuture> optional = rpcClient
                .connect();
        if(optional.isPresent()){
            optional.get().addListener((ChannelFutureListener) future -> {
                boolean success = future.isSuccess();
                logger.info("Rpc reconnect={}, failCount={}, currentChannelCount={}, info={}",
                        success? "success! ":"fail",
                        reconnectCount,
                        rpcClient.getActiveSocketChannelCount(),
                        causeMessage);
                if (success) {
                    reconnectCount = 0;
                    if(reconnectSuccessHandler != null){
                        reconnectSuccessHandler.accept(rpcClient);
                    }
                }
            }).sync();
        }
    }

    @Override
    public void run() {
        try {
            byte[] msg = rpcClient.getRpcCommandService().ping();
            if(isLogHeartEvent) {
                logger.info("{} The heartbeat packets : {}",rpcClient.getName(),new String(msg));
            }
        }catch (UndeclaredThrowableException e) {
            Throwable cause = e.getCause();
            if(cause instanceof RpcConnectException
                    || cause instanceof RpcTimeoutException){
                try {
                    reconnect(e.getMessage());
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex.getMessage(),ex);
                }
            }else if(cause instanceof InterruptedException){
                throw new RuntimeException(cause.getMessage(),cause);
            }
        } catch (RpcException e){
            logger.error(e.getMessage(),e);
        }
    }

    /**
     * Gets the thread scheduling executo
     * @return
     */
    private static ThreadPoolX getSchedulePool() {
        if(SCHEDULE_POOL == null){
            synchronized (RpcClient.class){
                if(SCHEDULE_POOL == null){
                    SCHEDULE_POOL = new ThreadPoolX("RpcClientHeartbeat",2,Thread.MAX_PRIORITY,true);
                }
            }
        }
        return SCHEDULE_POOL;
    }

    /**
     * Select the least frequently performed task
     * @param sourceTask The first task from the queue pops up
     * @return Perform the least frequent tasks
     */
    private static RpcClientHeartbeatTask chooseTask(RpcClientHeartbeatTask sourceTask){
        int minCount = 0;
        RpcClientHeartbeatTask chooseTask = sourceTask;
        for(Map.Entry<RpcClient,ScheduledQueueTask> entry : SCHEDULE_MAP.entrySet()){
            RpcClientHeartbeatTask theTask = entry.getValue().heartbeatTask;
            if(theTask == null){
                continue;
            }
            int currentMinCount = theTask.scheduleCount.get();
            if(currentMinCount <= 0){
                return theTask;
            }

            if(minCount == 0 || currentMinCount < minCount){
                minCount = currentMinCount;
                chooseTask = theTask;
            }
        }
        return chooseTask;
    }

    private static class ScheduledQueueTask implements Runnable{
        RpcClientHeartbeatTask heartbeatTask;
        ScheduledFuture scheduledFuture;

        ScheduledQueueTask(RpcClientHeartbeatTask heartbeatTask) {
            this.heartbeatTask = heartbeatTask;
        }

        void cancel(){
            scheduledFuture.cancel(false);
        }

        @Override
        public void run() {
            if(heartbeatTask != null && !TASK_QUEUE.contains(heartbeatTask)){
                TASK_QUEUE.add(heartbeatTask);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof RpcClientHeartbeatTask)){
            return false;
        }
        RpcClientHeartbeatTask that = (RpcClientHeartbeatTask) obj;
        return rpcClient.getRemoteAddress().equals(that.rpcClient.getRemoteAddress());
    }

    @Override
    public String toString() {
        return "RpcClientHeartbeatTask{" +
                "rpcClient=" + (rpcClient == null? "null":rpcClient.getName()) +
                ", scheduleCount=" + scheduleCount +
                '}';
    }

    static {
        long heartIntervalSecond = SystemPropertyUtil.getLong("netty-rpc.heartIntervalSecond",5L);
        getSchedulePool().scheduleWithFixedDelay(()->{
            try {
                RpcClientHeartbeatTask sourceTask = TASK_QUEUE.take();
                if(!TASK_QUEUE.contains(sourceTask)) {
                    RpcClientHeartbeatTask task = chooseTask(sourceTask);
                    task.scheduleCount.incrementAndGet();
                    task.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        },heartIntervalSecond,heartIntervalSecond,TimeUnit.SECONDS);
    }

}
