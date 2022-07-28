package com.github.netty.protocol.nrpc;

import java.util.concurrent.CompletableFuture;

public interface Emitter<RESULT, CHUNK> {
    <T> CompletableFuture<T> send(CHUNK chunk, Class<T> responseType, int responseTimeout);

    void send(CHUNK chunk);

    boolean complete(RESULT completeResult);

    boolean complete(Throwable throwable);

    boolean isComplete();

    int getSendCount();
}
