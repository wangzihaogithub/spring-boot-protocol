package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.nrpc.exception.RpcResponseException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import static com.github.netty.protocol.nrpc.RpcPacket.*;

/**
 * Simple Future
 * @author wangzihao
 */
public class RpcFuture implements Future<ResponsePacket>,Recyclable{
    private static final Recycler<RpcFuture> RECYCLER = new Recycler<>(RpcFuture::new);
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    private volatile ResponsePacket response;

    public static RpcFuture newInstance(){
        RpcFuture rpcFuture  = RECYCLER.getInstance();

        ResponsePacket rpcResponsePacket = rpcFuture.response;
        if(rpcResponsePacket != null){
            RecyclableUtil.release(rpcResponsePacket);
            rpcFuture.response = null;
        }
        return rpcFuture;
    }

    @Override
    public ResponsePacket get() throws InterruptedException, ExecutionException {
        TOTAL_COUNT.incrementAndGet();

        try {
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
        }catch (InterruptedException t){
            throw t;
        }catch (Throwable t){
            throw new ExecutionException(t);
        }

        //If an exception state is returned, an exception is thrown
        handlerResponseIfNeedThrow(response);
        return response;
    }

    /**
     * Get (note: block the current thread)
     * @param timeout timeout
     * @param timeUnit timeUnit
     * @return RpcResponse
     */
    @Override
    public ResponsePacket get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException,TimeoutException {
        TOTAL_COUNT.incrementAndGet();

        try {
            if (!isDone()) {
                lock.lock();
                try {
                    long start = System.currentTimeMillis();
                    while (!isDone()) {
                        done.await(timeout, TimeUnit.MILLISECONDS);
                        if (isDone() || System.currentTimeMillis() - start > timeout) {
                            break;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }catch (InterruptedException t){
            throw t;
        }catch (Throwable t){
            throw new ExecutionException(t);
        }

        if(!isDone()){
            String timeoutMessage = "RpcRequestTimeout : maxTimeout = [" + timeout + "], [" + toString() + "]";
            TimeoutException timeoutException = new TimeoutException(timeoutMessage);
            RpcTimeoutException rpcTimeoutException = new RpcTimeoutException(timeoutMessage, false);
            rpcTimeoutException.setStackTrace(timeoutException.getStackTrace());
            timeoutException.initCause(rpcTimeoutException);
            throw timeoutException;
        }

        //If an exception state is returned, an exception is thrown
        handlerResponseIfNeedThrow(response);
        return response;
    }

    /**
     * cancel
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

    public ResponsePacket getResult() {
        return response;
    }

    @Override
    public String toString() {
        return "RpcFuture{" +
                "response=" + response +
                '}';
    }

    /**
     * Has been completed
     * @param rpcResponse rpcResponse
     */
    void done(ResponsePacket rpcResponse){
        this.response = rpcResponse;
        this.lock.lock();
        try {
            this.done.signal();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * If an exception state is returned, an exception is thrown
     * All response states above 400 are in error
     */
    private void handlerResponseIfNeedThrow(ResponsePacket response) throws ExecutionException {
        if(response == null) {
            return;
        }

        Integer status = response.getStatus();
        if(status == null || status >= ResponsePacket.NO_SUCH_METHOD){
            RpcResponseException rpcResponseException = new RpcResponseException(status,response.getMessage(),false);
            ExecutionException exception = new ExecutionException(rpcResponseException);
            rpcResponseException.setStackTrace(exception.getStackTrace());
            throw exception;
        }
        TOTAL_SUCCESS_COUNT.incrementAndGet();
    }

    @Override
    public void recycle() {
        this.response = null;
        RECYCLER.recycleInstance(this);
    }

    /**
     * Spin success number
     */
    public static final AtomicLong TOTAL_SUCCESS_COUNT = new AtomicLong();
    /**
     * Total number of calls
     */
    public static final AtomicLong TOTAL_COUNT = new AtomicLong();

}
