package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.Recycler;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.github.netty.protocol.nrpc.RpcPacket.ResponseLastPacket;
import static com.github.netty.protocol.nrpc.codec.DataCodec.Encode.BINARY;

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
    private static final Recycler<RpcClientFuture> RECYCLER = new Recycler<>(RpcClientFuture::new);
    public static int SPIN_LOCK_COUNT = SystemPropertyUtil.getInt("netty-rpc.clientFuture.spinLockCount", 0);
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    private final AtomicInteger chunkIndex = new AtomicInteger();
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
        rpcClientFuture.chunkIndex.set(0);
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
    public void chunk(RpcPacket.ResponseChunkPacket rpcResponse, ChunkAck ack) {
        RpcContext<RpcClient> rpcContext = this.rpcContext;
        RpcMethod<RpcClient> rpcMethod = rpcContext.getRpcMethod();
        try {
            int chunkId = rpcResponse.getChunkId();
            int chunkIndex = this.chunkIndex.getAndIncrement();
            Supplier<Object> chunkSupplier;
            byte[] data = rpcResponse.getData();
            if (rpcResponse.getEncode() == BINARY) {
                chunkSupplier = new LazyChunk(null, data, true, null);
            } else {
                chunkSupplier = new LazyChunk(data, null, false, rpcMethod);
            }
            // call aop
            for (RpcClientAop aop : rpcMethod.getInstance().getAopList()) {
                try {
                    aop.onChunkAfter(rpcContext, chunkSupplier, chunkIndex, chunkId, ack);
                } catch (Exception e) {
                    rpcMethod.getLog().warn(rpcMethod + " client.aop.onChunkAfter() exception = {}", e.toString(), e);
                }
            }
        } finally {
            if (!ack.isAck()) {
                ack.ack();
            }
            RecyclableUtil.release(rpcResponse);
        }
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

    private static class LazyChunk implements Supplier<Object> {
        private byte[] data;
        private Object chunk;
        private boolean resolved;
        private RpcMethod<RpcClient> rpcMethod;

        LazyChunk(byte[] data, Object chunk, boolean resolved, RpcMethod<RpcClient> rpcMethod) {
            this.data = data;
            this.chunk = chunk;
            this.resolved = resolved;
            this.rpcMethod = rpcMethod;
        }

        @Override
        public Object get() {
            if (!resolved) {
                byte[] data = this.data;
                RpcMethod<RpcClient> rpcMethod = this.rpcMethod;
                if (data != null && rpcMethod != null) {
                    this.chunk = rpcMethod.getInstance().getDataCodec().decodeChunkResponseData(data, rpcMethod);
                    this.resolved = true;
                    this.data = null;
                    this.rpcMethod = null;
                }
            }
            return this.chunk;
        }
    }

}
