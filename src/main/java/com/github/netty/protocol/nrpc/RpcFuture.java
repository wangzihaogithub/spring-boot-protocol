package com.github.netty.protocol.nrpc;

import com.github.netty.core.CoreConstants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.github.netty.protocol.nrpc.RpcClient.FUTURE_MAP_ATTR;
import static com.github.netty.protocol.nrpc.RpcPacket.RequestPacket;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket;

/**
 * Simple Future
 * @author wangzihao
 */
public class RpcFuture {
    private Lock lock;
    private Condition done;
    private RequestPacket request;
    private ResponsePacket response;
    private Channel channel;
    private AtomicBoolean cancelFlag = new AtomicBoolean();
    private Consumer<ResponsePacket> responseConsumer;

    public RpcFuture(RequestPacket request, Channel channel) {
        this.request = request;
        this.channel = channel;
    }

    /**
     * Get (note: no block the current thread)
     * @param responseConsumer responseConsumer
     */
    public void get(Consumer<ResponsePacket> responseConsumer) {
        this.responseConsumer = responseConsumer;
    }

    /**
     * Get (note: block the current thread)
     * @param timeout timeout
     * @param timeUnit timeUnit
     * @return RpcResponse
     */
    public ResponsePacket get(int timeout, TimeUnit timeUnit) {
        if(response != null){
            return response;
        }
        lock = new ReentrantLock();
        done = lock.newCondition();

        channel.attr(FUTURE_MAP_ATTR).get().put(request.getRequestId(), this);
        channel.writeAndFlush(request).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

        TOTAL_INVOKE_COUNT.incrementAndGet();
        //Spin, because if it's a local RPC call, it's too fast and there's no need to jam
        int spinCount = CoreConstants.getRpcLockSpinCount();
        if (spinCount > 0) {
            for (int i = 0; response == null && i < spinCount; i++) {
                Thread.yield();
            }
        }

        if (response == null) {
            try {
                if (response == null) {
                    lock.lock();
                    try {
                        done.await(timeout, timeUnit);
                    }finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //If the spin gets the response back directly
        if (response != null) {
            TOTAL_SPIN_RESPONSE_COUNT.incrementAndGet();
            return response;
        }
        TIMEOUT_API.put(request.getMethodName(), TIMEOUT_API.getOrDefault(request.getMethodName(), 0) + 1);
        return null;
    }

    /**
     * cancel
     * @return if the task could not be cancelled, typically because it has already completed normally;
     */
    public boolean cancel(){
        if(cancelFlag.compareAndSet(false,true)){
            Map<Integer,RpcFuture> futureMap = channel.attr(FUTURE_MAP_ATTR).get();
            if(futureMap != null) {
                futureMap.remove(request.getRequestId());
            }
            return true;
        }
        return false;
    }

    /**
     * Has been completed
     * @param rpcResponse rpcResponse
     */
    public void done(ResponsePacket rpcResponse){
        if(cancelFlag.get()){
            return;
        }

        this.response = rpcResponse;
        if(done != null) {
            lock.lock();
            try {
                done.signal();
            }finally {
                lock.unlock();
            }
        }
        if(responseConsumer != null){
            responseConsumer.accept(rpcResponse);
        }
    }

    public RequestPacket getRequest() {
        return request;
    }

    public ResponsePacket getResponse() {
        return response;
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

    @Override
    public String toString() {
        return "RpcFuture{" +
                "request=" + request +
                ", response=" + response +
                ", channel=" + channel +
                '}';
    }
}
