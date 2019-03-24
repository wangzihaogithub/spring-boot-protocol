package com.github.netty.protocol.nrpc;

import com.github.netty.core.CoreConstants;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.nrpc.exception.RpcResponseException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.netty.protocol.nrpc.RpcClient.FUTURE_MAP_ATTR;

/**
 * Simple Future
 * @author wangzihao
 */
public class RpcFuture implements Future<RpcResponsePacket>,Recyclable{
    private static final Recycler<RpcFuture> RECYCLER = new Recycler<>(RpcFuture::new);
    private Lock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();
    private RpcRequestPacket request;
    private RpcResponsePacket result;
    private Channel channel;
    private Map<Integer,RpcFuture> futureMap;

    public static RpcFuture newInstance(RpcRequestPacket request, Channel channel){
        RpcFuture rpcFuture = RECYCLER.getInstance();
        rpcFuture.request = request;
        rpcFuture.channel = channel;
        rpcFuture.futureMap = channel.attr(FUTURE_MAP_ATTR).get();

        return rpcFuture;
    }

    @Override
    public RpcResponsePacket get() throws InterruptedException, ExecutionException {
        checkCanUse();

        RpcResponsePacket response;
        try {
            ChannelFuture channelFuture = sendToChannel();
            spin();

            //If the spin gets the response back directly
            if (result == null) {
                lock.lock();
                try {
                    condition.await();
                } finally {
                    lock.unlock();
                }
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
    public RpcResponsePacket get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException,TimeoutException {
        checkCanUse();

        RpcResponsePacket response;
        try {
            ChannelFuture channelFuture = sendToChannel();
            spin();

            if (result == null && timeout != 0) {
                lock.lock();
                try {
                    if (timeout > 0) {
                        condition.await(timeout, timeUnit);
                    } else {
                        condition.await();
                    }
                } finally {
                    lock.unlock();
                }
            }
        }catch (InterruptedException t){
            throw t;
        }catch (Throwable t){
            throw new ExecutionException(t);
        }finally {
            response = this.result;
            recycle();
        }

        if (response == null) {
            TimeoutException timeoutException = new TimeoutException();
            RpcTimeoutException rpcTimeoutException = new RpcTimeoutException("RpcRequestTimeout : maxTimeout = [" + timeout + "], [" + toString() + "]", false);
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
        throw new UnsupportedOperationException("Unsupported isDone()");
    }

    @Override
    public String toString() {
        return "RpcFuture{" +
                "request=" + request +
                ", response=" + result +
                ", channel=" + channel +
                '}';
    }

    /**
     * Has been completed
     * @param rpcResponse rpcResponse
     */
    public void done(RpcResponsePacket rpcResponse){
        checkCanUse();

        this.result = rpcResponse;
        lock.lock();
        try {
            condition.signal();
        }finally {
            lock.unlock();
        }
    }

    /**
     * write to netty channel
     */
    private ChannelFuture sendToChannel(){
        futureMap.put(request.getRequestIdInt(), this);
        return channel.writeAndFlush(request)
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    /**
     * Spin, because if it's a local RPC call, it's too fast and there's no need to jam
     */
    private void spin(){
        int spinCount = CoreConstants.getRpcLockSpinCount();
        if (spinCount > 0) {
            for (int i = 0; result == null && i < spinCount; i++) {
                Thread.yield();
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
    }

    private void checkCanUse(){
        if(channel == null || request == null){
            throw new IllegalStateException("It has been recycled and cannot be reused. You need to reconstruct one for use");
        }
    }

    @Override
    public void recycle() {
        this.futureMap = null;
        this.request = null;
        this.result = null;
        this.channel = null;
        RECYCLER.recycleInstance(this);
    }
}
