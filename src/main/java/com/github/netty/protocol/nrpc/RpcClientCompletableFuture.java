package com.github.netty.protocol.nrpc;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * support CompletableFuture async response.
 *
 * @author wangzihao
 * 2020/05/30/019
 */
public class RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> extends CompletableFuture<COMPLETE_RESULT> {
    private final Collection<Consumer<CHUNK>> chunkConsumerList = new LinkedList<>();

    RpcClientCompletableFuture(RpcClientReactivePublisher source) {
        source.subscribe(new SubscriberAdapter(this));
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(Consumer<CHUNK> consumer) {
        getChunkConsumerList().add(consumer);
        return this;
    }

    public Collection<Consumer<CHUNK>> getChunkConsumerList() {
        return chunkConsumerList;
    }

    public void callbackChunkConsumerList(CHUNK chunk) {
        for (Consumer<CHUNK> chunkConsumer : getChunkConsumerList()) {
            chunkConsumer.accept(chunk);
        }
    }

    public static class SubscriberAdapter<RESULT, CHUNK> implements Subscriber<RESULT>, ChunkListener<CHUNK> {
        private final RpcClientCompletableFuture<RESULT, CHUNK> completableFuture;
        private RESULT result;
        private Throwable throwable;

        private SubscriberAdapter(RpcClientCompletableFuture<RESULT, CHUNK> completableFuture) {
            this.completableFuture = completableFuture;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
        }

        @Override
        public void onChunk(CHUNK chunk) {
            completableFuture.callbackChunkConsumerList(chunk);
        }

        @Override
        public void onNext(RESULT o) {
            this.result = o;
        }

        @Override
        public void onError(Throwable t) {
            this.throwable = t;
        }

        @Override
        public void onComplete() {
            Throwable throwable = this.throwable;
            RESULT result = this.result;
            this.throwable = null;
            this.result = null;
            if (throwable != null) {
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(result);
            }
        }
    }
}
