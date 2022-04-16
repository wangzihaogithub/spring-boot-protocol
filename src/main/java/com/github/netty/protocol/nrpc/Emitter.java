package com.github.netty.protocol.nrpc;

import java.util.function.BiConsumer;

public interface Emitter<RESULT, CHUNK> {
    void send(CHUNK chunk);
    void complete(RESULT lastResult);
    void setSendHandler(BiConsumer<Object, State> sendHandler);
}
