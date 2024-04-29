package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractNettyClient;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelUtils;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.internal.PlatformDependent;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class DubboClient extends AbstractNettyClient {
    private final String serviceName;
    private final AtomicBoolean scheduleReconnectTaskIngFlag = new AtomicBoolean(false);
    private boolean enableRpcHeartLog = true;
    private long connectTimeout = 1000;
    private int reconnectScheduledIntervalMs = 5000;
    private boolean enableReconnectScheduledTask = false;
    /**
     * Connection status
     */
    private volatile State state = State.DOWN;
    private ChannelHandler handler;
    /**
     * reconnectScheduleFuture
     */
    private ScheduledFuture<?> reconnectScheduleFuture;
    private long reconnectCount = 0;
    /**
     * reconnectTaskSuccessConsumer Callback method after successful reconnect
     */
    private BiConsumer<Long, DubboClient> reconnectTaskSuccessConsumer;
    /**
     * Connecting timeout timestamp
     */
    private volatile long connectTimeoutTimestamp;

    public DubboClient(String serviceName, ChannelHandler channelInitializer) {
        super(serviceName, null);
        this.serviceName = serviceName;
        this.handler = channelInitializer;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    protected ChannelHandler newBossChannelHandler() {
        return handler;
    }

    public DubboClient handler(ChannelHandler handler) {
        this.handler = handler;
        return this;
    }

    @Override
    public SocketChannel getChannel() {
        SocketChannel socketChannel = super.getChannel();
        if (socketChannel == null || !socketChannel.isActive()) {
            long timestamp = System.currentTimeMillis();
            socketChannel = waitGetConnect(connect(), connectTimeout);
            if (!socketChannel.isActive()) {
                if (enableReconnectScheduledTask) {
                    scheduleReconnectTask(reconnectScheduledIntervalMs, TimeUnit.MILLISECONDS);
                }
                throw new DubboConnectException("The [" + socketChannel + "] channel no connect. maxConnectTimeout=[" + connectTimeout + "], connectTimeout=[" + (System.currentTimeMillis() - timestamp) + "]");
            }
        }

        int yieldCount = 0;
        if (!socketChannel.isWritable()) {
            socketChannel.flush();
        }
        while (!socketChannel.isWritable()) {
            ChannelUtils.forceFlush(socketChannel);
            if (!socketChannel.eventLoop().inEventLoop()) {
                Thread.yield();
                yieldCount++;
            }
        }
        if (yieldCount != 0 && enableRpcHeartLog && logger.isDebugEnabled()) {
            logger.debug("RpcClient waitWritable... yieldCount={}", yieldCount);
        }
        return socketChannel;
    }

    @Override
    public void setChannel(SocketChannel newChannel) {
        super.setChannel(newChannel);
        state = State.UP;
    }

    public boolean scheduleReconnectTask(long reconnectIntervalMillSeconds, TimeUnit timeUnit) {
        if (this.scheduleReconnectTaskIngFlag.compareAndSet(false, true)) {
            this.reconnectScheduleFuture = getWorker().scheduleWithFixedDelay(() -> {
                if (state == State.UP) {
                    cancelScheduleReconnectTask();
                } else {
                    reconnectCount++;
                    connect();
                }
            }, reconnectIntervalMillSeconds, reconnectIntervalMillSeconds, timeUnit);
            return true;
        }
        return false;
    }

    public boolean isEnableReconnectScheduledTask() {
        return enableReconnectScheduledTask;
    }

    public void setEnableReconnectScheduledTask(boolean enableReconnectScheduledTask) {
        this.enableReconnectScheduledTask = enableReconnectScheduledTask;
    }

    public boolean isEnableRpcHeartLog() {
        return enableRpcHeartLog;
    }

    public void setEnableRpcHeartLog(boolean enableRpcHeartLog) {
        this.enableRpcHeartLog = enableRpcHeartLog;
    }

    public int getReconnectScheduledIntervalMs() {
        return reconnectScheduledIntervalMs;
    }

    public void setReconnectScheduledIntervalMs(int reconnectScheduledIntervalMs) {
        this.reconnectScheduledIntervalMs = reconnectScheduledIntervalMs;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void cancelScheduleReconnectTask() {
        ScheduledFuture scheduledFuture = this.reconnectScheduleFuture;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        BiConsumer<Long, DubboClient> reconnectSuccessHandler = this.reconnectTaskSuccessConsumer;
        if (reconnectSuccessHandler != null) {
            reconnectSuccessHandler.accept(reconnectCount, this);
        }
        this.reconnectScheduleFuture = null;
        this.reconnectCount = 0;
        this.scheduleReconnectTaskIngFlag.set(false);
    }

    public void setReconnectTaskSuccessConsumer(BiConsumer<Long, DubboClient> reconnectTaskSuccessConsumer) {
        this.reconnectTaskSuccessConsumer = reconnectTaskSuccessConsumer;
    }

    protected SocketChannel waitGetConnect(Optional<ChannelFuture> optional, long connectTimeout) {
        if (optional.isPresent()) {
            connectTimeoutTimestamp = System.currentTimeMillis();
            ChannelFuture future = optional.get();
            try {
                future.await(connectTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                PlatformDependent.throwException(e);
            } finally {
                connectTimeoutTimestamp = 0;
            }
            return (SocketChannel) future.channel();
        } else {
            int yieldCount = 0;
            long timeoutTimestamp = connectTimeoutTimestamp;
            long waitTime;
            while (timeoutTimestamp != 0 && (waitTime = timeoutTimestamp - System.currentTimeMillis()) > 0) {
                if (waitTime > 200) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        PlatformDependent.throwException(e);
                    }
                } else {
                    yieldCount++;
                    Thread.yield();
                }
            }
            while (state != State.UP) {
                yieldCount++;
                Thread.yield();
            }
            if (enableRpcHeartLog && logger.isDebugEnabled()) {
                logger.debug("RpcClient waitGetConnect... yieldCount={}", yieldCount);
            }
            return super.getChannel();
        }
    }

    @Override
    public String toString() {
        return serviceName + remoteAddress + "(" + state + ")";
    }

    /**
     * Client connection status
     */
    public enum State {
        DOWN, UP
    }

    /**
     * DubboConnectException
     *
     * @author wangzihao
     * 2024/4/29
     */
    public static class DubboConnectException extends RuntimeException {

        public DubboConnectException(String message) {
            super(message, null, false, false);
        }

        public DubboConnectException(String message, Throwable cause) {
            super(message, cause, false, false);
            if (cause != null) {
                setStackTrace(cause.getStackTrace());
            }
        }
    }
}