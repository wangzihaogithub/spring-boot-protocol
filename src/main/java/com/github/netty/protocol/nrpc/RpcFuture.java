package com.github.netty.protocol.nrpc;

import com.github.netty.core.CoreConstants;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.nrpc.exception.RpcResponseException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple Future
 * @author wangzihao
 */
public class RpcFuture<T extends RpcResponsePacket> implements Future<T>,Recyclable{
    private static final FastThreadLocal<Recycler<RpcFuture<RpcResponsePacket>>> RECYCLER_LOCAL = new FastThreadLocal<Recycler<RpcFuture<RpcResponsePacket>>>(){
        @Override
        protected Recycler<RpcFuture<RpcResponsePacket>> initialValue() throws Exception {
            return new Recycler<>(RpcFuture::new);
        }
    };
    private Recycler<RpcFuture<T>> recycler;
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    private RpcRequestPacket request;
    private T result;

    public static RpcFuture<RpcResponsePacket> newInstance(RpcRequestPacket request){
        Recycler<RpcFuture<RpcResponsePacket>> recycler = RECYCLER_LOCAL.get();

        RpcFuture<RpcResponsePacket> rpcFuture  = recycler.getInstance();
        rpcFuture.recycler = recycler;
        rpcFuture.request = request;
        return rpcFuture;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        checkCanUse();

        T response;
        try {
            spin();

            //If the spin gets the response back directly
            if (result == null) {
                done.await();
            }
        }catch (InterruptedException t){
            throw t;
        }catch (Throwable t){
            throw new ExecutionException(t);
        }finally {
            response = result;
            recycle();
        }

        //If an exception state is returned, an exception is thrown
        handlerResponseIfNeedThrow(response);
        return result;
    }

    /**
     * Get (note: block the current thread)
     * @param timeout timeout
     * @param timeUnit timeUnit
     * @return RpcResponse
     */
    @Override
    public T get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException,TimeoutException {
        TOTAL_INVOKE_COUNT.incrementAndGet();
        String methodName = request.getMethodNameString();

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
            TIMEOUT_API.put(methodName,TIMEOUT_API.getOrDefault(methodName,0) + 1);
            TimeoutException timeoutException = new TimeoutException();
            RpcTimeoutException rpcTimeoutException = new RpcTimeoutException("RpcRequestTimeout : maxTimeout = [" + timeout + "], [" + toString() + "]", false);
            rpcTimeoutException.setStackTrace(timeoutException.getStackTrace());
            timeoutException.initCause(rpcTimeoutException);
            throw timeoutException;
        }

        //If an exception state is returned, an exception is thrown
        handlerResponseIfNeedThrow(result);
        return result;
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
        return result != null;
    }

    @Override
    public String toString() {
        return "RpcFuture{" +
                "result=" + result +
                '}';
    }

    /**
     * Has been completed
     * @param rpcResponse rpcResponse
     */
    public void done(T rpcResponse){
        if(request == null){
            RecyclableUtil.release(rpcResponse);
            return;
        }

        this.result = rpcResponse;
        this.lock.lock();
        try {
            this.done.signal();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Spin, because if it's a local RPC call, it's too fast and there's no need to jam
     */
    private void spin(){
        int spinCount = CoreConstants.getRpcLockSpinCount();
        if (spinCount > 0) {
            for (int i = 0; result == null && i < spinCount; i++) {
//                Thread.yield();
            }
        }
    }

    /**
     * If an exception state is returned, an exception is thrown
     * All response states above 400 are in error
     */
    private void handlerResponseIfNeedThrow(RpcResponsePacket response) throws ExecutionException {
        if(response == null) {
            return;
        }

        RpcResponseStatus status = response.getStatus();
        if(status.getCode() >= RpcResponseStatus.NO_SUCH_METHOD.getCode()){
            RpcResponseException rpcResponseException = new RpcResponseException(status,response.getMessageString(),false);
            ExecutionException exception = new ExecutionException(rpcResponseException);
            rpcResponseException.setStackTrace(exception.getStackTrace());
            throw exception;
        }
        TOTAL_SPIN_RESPONSE_COUNT.incrementAndGet();
    }

    private void checkCanUse(){
        if(request == null){
            throw new IllegalStateException("It has been recycled and cannot be reused. You need to reconstruct one for use");
        }
    }

    @Override
    public void recycle() {
        this.request = null;
        this.result = null;
        this.recycler.recycleInstance(this);
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
