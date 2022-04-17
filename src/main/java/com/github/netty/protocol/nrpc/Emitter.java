package com.github.netty.protocol.nrpc;

public interface Emitter<RESULT, CHUNK> {
    void send(CHUNK chunk);
    void complete(RESULT completeResult);
}
