package com.github.netty.protocol.nrpc;

import com.github.netty.core.CoreConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import static com.github.netty.protocol.nrpc.RpcPacket.RequestPacket;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket;

/**
 * Simple Future
 * @author wangzihao
 */
public class RpcFuture {
    private ResponsePacket rpcResponse;
    public long beginTime;
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    private RequestPacket rpcRequest;

    public RpcFuture(RequestPacket rpcRequest) {
        this.rpcRequest = rpcRequest;
    }

    /**
     * Get (note: block the current thread)
     * @param timeout timeout
     * @param timeUnit timeUnit
     * @return RpcResponse
     */
    public ResponsePacket get(int timeout, TimeUnit timeUnit) {
        TOTAL_INVOKE_COUNT.incrementAndGet();
        //Spin, because if it's a local RPC call, it's too fast and there's no need to jam
        int spinCount = CoreConstants.getRpcLockSpinCount();
        if(spinCount > 0) {
            for (int i = 0; rpcResponse == null && i < spinCount; i++) {
                Thread.yield();
            }
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

        //If the spin gets the response back directly
        if(rpcResponse != null){
            TOTAL_SPIN_RESPONSE_COUNT.incrementAndGet();
            return rpcResponse;
        }
        TIMEOUT_API.put(rpcRequest.getMethodName(),TIMEOUT_API.getOrDefault(rpcRequest.getMethodName(),0) + 1);
        return null;
    }

    /**
     * Has been completed
     * @param rpcResponse rpcResponse
     */
    public void done(ResponsePacket rpcResponse){
        this.lock.lock();
        try {
            this.rpcResponse = rpcResponse;
            this.done.signal();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Spin success number
     */
    public static final AtomicLong TOTAL_SPIN_RESPONSE_COUNT = new AtomicLong();
    /**
     * Total number of calls
     */
    public static final AtomicLong TOTAL_INVOKE_COUNT = new AtomicLong();
    /**
     * Timeout API
     */
    public static final Map<String,Integer> TIMEOUT_API = new ConcurrentHashMap<>();
}
