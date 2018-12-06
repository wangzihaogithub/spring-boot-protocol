package com.github.netty.register.rpc;

import com.github.netty.core.constants.CoreConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 简易版的 Future
 * @author 84215
 */
public class RpcFuture {

    private RpcResponse rpcResponse;
    public long beginTime;
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    private RpcRequest rpcRequest;

    public RpcFuture(RpcRequest rpcRequest) {
        this.rpcRequest = rpcRequest;
    }

    /**
     * 获取 (注: 堵塞当前线程)
     * @param timeout
     * @param timeUnit
     * @return
     */
    public RpcResponse get(int timeout, TimeUnit timeUnit) {
        TOTAL_INVOKE_COUNT.incrementAndGet();
        //自旋, 因为如果是本地rpc调用,速度太快了, 没必要再堵塞
        int spinCount = CoreConstants.getRpcLockSpinCount();
        for (int i=0; rpcResponse == null && i<spinCount; i++){
            //
        }

        beginTime = System.currentTimeMillis();
        if (rpcResponse == null) {
            lock.lock();
            try {
                if (rpcResponse == null) {
                    done.await(timeout, timeUnit);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }

        //如果自旋后拿到响应 直接返回
        if(rpcResponse != null){
            TOTAL_SPIN_RESPONSE_COUNT.incrementAndGet();
            return rpcResponse;
        }
        TIMEOUT_API.put(rpcRequest.getMethodName(),TIMEOUT_API.getOrDefault(rpcRequest.getMethodName(),0) + 1);
        return null;
    }

    /**
     * 已完成
     * @param rpcResponse RPC响应
     */
    public void done(RpcResponse rpcResponse){
        this.lock.lock();
        try {
            this.rpcResponse = rpcResponse;
            this.done.signal();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * 自旋成功数
     */
    public static final AtomicLong TOTAL_SPIN_RESPONSE_COUNT = new AtomicLong();
    //总调用次数
    public static final AtomicLong TOTAL_INVOKE_COUNT = new AtomicLong();
    //超时api
    public static final Map<String,Integer> TIMEOUT_API = new ConcurrentHashMap<>();
}
