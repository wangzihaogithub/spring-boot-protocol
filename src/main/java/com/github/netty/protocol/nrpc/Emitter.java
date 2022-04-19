package com.github.netty.protocol.nrpc;

import java.util.concurrent.CompletableFuture;

public interface Emitter<RESULT, CHUNK> {
    <T> CompletableFuture<T> send(Object chunk, Class<T> type, int timeout);
    void send(CHUNK chunk);
    void complete(RESULT completeResult);
}
