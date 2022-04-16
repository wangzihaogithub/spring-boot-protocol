package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.Recycler;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.netty.protocol.nrpc.RpcPacket.ResponseLastPacket;

/**
 * Simple Future
 *
 * @author wangzihao
 */
public class RpcClientFuture implements Future<ResponseLastPacket>, RpcDone, Recyclable {
    /**
     * Total number of calls
     */
    public static final LongAdder TOTAL_COUNT = new LongAdder();
    public static final LongAdder TOTAL_SUCCESS_COUNT = new LongAdder();
    public static int SPIN_LOCK_COUNT = SystemPropertyUtil.getInt("netty-rpc.clientFuture.spinLockCount", 0);
    private static final Recycler<RpcClientFuture> RECYCLER = new Recycler<>(RpcClientFuture::new);
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    private volatile ResponseLastPacket response;
    private RpcContext<RpcClient> rpcContext;

    public static RpcClientFuture newInstance(RpcContext<RpcClient> rpcContext) {
        RpcClientFuture rpcClientFuture = RECYCLER.getInstance();

        ResponseLastPacket rpcResponsePacket = rpcClientFuture.response;
        if (rpcResponsePacket != null) {
            RecyclableUtil.release(rpcResponsePacket);
            rpcClientFuture.response = null;
        }
        rpcClientFuture.rpcContext = rpcContext;
        return rpcClientFuture;
    }

    @Override
    public ResponseLastPacket get() throws InterruptedException {
        TOTAL_COUNT.increment();

        for (int i = 0; i < SPIN_LOCK_COUNT; i++) {
            // yield CPU time.
            Thread.yield();
            if (isDone()) {
                break;
            }
        }
        if (!isDone()) {
            lock.lock();
            try {
                while (!isDone()) {
                    done.await();
                    if (isDone()) {
                        break;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        //If an exception state is returned, an exception is thrown
        handlerResponseIfNeedThrow(response);
        TOTAL_SUCCESS_COUNT.increment();
        return response;
    }

    /**
     * Get (note: block the current thread)
     *
     * @param timeout  timeout
     * @param timeUnit timeUnit
     * @return RpcResponse
     */
    @Override
    public ResponseLastPacket get(long timeout, TimeUnit timeUnit) throws InterruptedException {
        TOTAL_COUNT.increment();

        for (int i = 0; i < SPIN_LOCK_COUNT; i++) {
            // yield CPU time.
            Thread.yield();
            if (isDone()) {
                break;
            }
        }
        long startTimestamp = System.currentTimeMillis();
        if (!isDone()) {
            lock.lock();
            try {
                while (!isDone()) {
                    done.await(timeout, TimeUnit.MILLISECONDS);
                    if (isDone() || System.currentTimeMillis() - startTimestamp > timeout) {
                        break;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        if (!isDone()) {
            long expiryTimestamp = System.currentTimeMillis();
            throw new RpcTimeoutException("RpcRequestTimeout : maxTimeout = [" + timeout +
                    "], timeout = [" + (expiryTimestamp - startTimestamp) + "], [" + toString() + "]", true,
                    startTimestamp, expiryTimestamp);
        }

        //If an exception state is returned, an exception is thrown
        handlerResponseIfNeedThrow(response);
        TOTAL_SUCCESS_COUNT.increment();
        return response;
    }

    /**
     * cancel
     *
     * @return if the task could not be cancelled, typically because it has already completed normally;
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException("Unsupported cancel()");
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException("Unsupported isCancelled()");
    }

    @Override
    public boolean isDone() {
        return response != null;
    }

    public ResponseLastPacket getResult() {
        return response;
    }

    @Override
    public String toString() {
        return "RpcClientFuture{" +
                "request=" + rpcContext.getRequest() +
                ",response=" + response +
                '}';
    }

    @Override
    public void chunk(RpcPacket.ResponseChunkPacket rpcResponse) {
        RecyclableUtil.release(rpcResponse);
    }

    /**
     * Has been completed
     *
     * @param rpcResponse rpcResponse
     */
    @Override
    public void done(RpcPacket.ResponseLastPacket rpcResponse) {
        this.response = rpcResponse;
        this.lock.lock();
        try {
            this.done.signal();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void doneTimeout(int requestId, long createTimestamp, long expiryTimestamp) {
        done(null);
    }

    @Override
    public void recycle() {
        this.response = null;
        this.rpcContext = null;
        RECYCLER.recycleInstance(this);
    }

}
