package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.util.concurrent.TimeUnit;

/**
 * An abstract Protocols Register
 *
 * @author wangzihao
 */
public abstract class AbstractProtocol implements ProtocolHandler, ServerListener {
    /**
     * Refresh buffer data interval (milliseconds),
     * the benefits of open time to send is sent in bulk to bring high-throughput,
     * but there will be a delay.
     * (If the timer is greater than 0 seconds to transmit buffer data is less than 0 seconds to transmit the data in real time)
     */
    private int autoFlushIdleMs;

    public int getAutoFlushIdleMs() {
        return autoFlushIdleMs;
    }

    public void setAutoFlushIdleMs(int autoFlushIdleMs) {
        this.autoFlushIdleMs = autoFlushIdleMs;
    }

    @Override
    public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
        int autoFlushIdleTime = getAutoFlushIdleMs();
        if (autoFlushIdleTime > 0) {
            channel.pipeline().addLast("autoflush", new AutoFlushChannelHandler(autoFlushIdleTime, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public String toString() {
        return getProtocolName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
