package com.github.netty.protocol.nrpc;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

public interface ChunkAck {
    ChunkAck DONT_NEED_ACK = new ChunkAck() {
        @Override
        public Promise ack(Object ack) {
            Promise promise = GlobalEventExecutor.INSTANCE.newPromise();
            promise.setSuccess(null);
            return promise;
        }

        @Override
        public void ack() {

        }

        @Override
        public boolean isAck() {
            return true;
        }
    };

    Promise ack(Object ack);

    boolean isAck();

    default void ack() {
        ack(null);
    }

}
