package com.github.netty.protocol.nrpc;

import io.netty.channel.ChannelFuture;

public interface ChunkAck {
    ChannelFuture ack(Object ack);
}
