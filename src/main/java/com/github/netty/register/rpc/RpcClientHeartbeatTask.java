package com.github.netty.register.rpc;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.core.util.ThreadPoolX;
import com.github.netty.register.rpc.exception.RpcConnectException;
import com.github.netty.register.rpc.exception.RpcTimeoutException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 * 心跳任务 (包含自动重连功能)
 *
 * @author 84215
 */
public class RpcClientHeartbeatTask implements Runnable{

    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    /**
     * RPC客户端
     */
    private RpcClient rpcClient;
    /**
     * 重连成功的回调方法
     */
    private Consumer<RpcClient> reconnectSuccessHandler;
    /**
     * 重连次数
     */
    private int reconnectCount;
    /**
     * 最大超时重试次数
     */
    private int maxTimeoutRetryNum = 3;
    /**
     * 被调用过的次数
     */
    private AtomicInteger scheduleCount = new AtomicInteger();
    /**
     * 调度线程池
     */
    private static ThreadPoolX SCHEDULE_POOL;
    /**
     * 客户端与心跳任务的关系映射,一个客户端对象只有一个心跳任务, 如果已经存在任务, 新任务会覆盖旧任务。
     */
    private static final Map<RpcClient,ScheduledQueueTask> SCHEDULE_MAP = new HashMap<>();
    /**
     * 心跳任务队列
     */
    private static final BlockingQueue<RpcClientHeartbeatTask> TASK_QUEUE = new LinkedBlockingQueue<>();
    /**
     * 是否日心跳事件
     */
    private boolean isLogHeartEvent;

    private RpcClientHeartbeatTask(RpcClient rpcClient, Consumer<RpcClient> reconnectSuccessHandler, boolean isLogHeartEvent) {
        this.rpcClient = rpcClient;
        this.reconnectSuccessHandler = reconnectSuccessHandler;
        this.isLogHeartEvent = isLogHeartEvent;
    }

    /**
     * 分配重连任务
     * @param heartIntervalSecond
     * @param timeUnit
     * @param reconnectSuccessHandler
     * @param rpcClient
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
     * 重连
     * @param causeMessage 重连原因
     * @return 是否成功
     */
    private boolean reconnect(String causeMessage){
        int count = ++reconnectCount;
        boolean success = rpcClient.connect();

        logger.info("第[" + count + "]次断线重连 :" + (success?"成功! 共保持"+rpcClient.getActiveSocketChannelCount()+"个连接":"失败") +", 重连原因["+ causeMessage +"]");
        if (success) {
            reconnectCount = 0;
            if(reconnectSuccessHandler != null){
                reconnectSuccessHandler.accept(rpcClient);
            }
        }
        return success;
    }

    @Override
    public void run() {
        try {
            byte[] msg = rpcClient.getRpcCommandService().ping();
            if(isLogHeartEvent) {
                logger.info(rpcClient.getName() + " 心跳包 : " + new String(msg));
            }
        }catch (RpcConnectException e) {
            reconnect(e.getMessage());

        }catch (RpcTimeoutException e){
            //重ping N次, 如果N次后还ping不通, 则进行重连
            for(int i = 0; i< maxTimeoutRetryNum; i++) {
                try {
                    byte[] msg = rpcClient.getRpcCommandService().ping();
                    return;
                } catch (RpcConnectException e1) {
                    reconnect(e1.getMessage());
                    return;
                }catch (RpcTimeoutException e2){
                    //
                }
            }
            reconnect(e.getMessage());
        }catch (Exception e){
            logger.error(e.getMessage(),e);
        }
    }

    /**
     * 获取线程调度执行器, 注: 延迟创建
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
     * 选出执行次数最少的任务
     * @param sourceTask 队列弹出的第一个任务
     * @return 执行次数最少的任务
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
            while (true) {
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
            }
        },heartIntervalSecond,heartIntervalSecond,TimeUnit.SECONDS);
    }

}
