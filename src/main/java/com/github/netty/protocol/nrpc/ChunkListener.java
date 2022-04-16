package com.github.netty.protocol.nrpc;

@FunctionalInterface
public interface ChunkListener<CHUNK> {
    void onChunk(CHUNK chunk);
}
