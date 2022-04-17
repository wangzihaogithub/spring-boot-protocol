package com.github.netty.protocol.nrpc;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * support CompletableFuture async response.
 *
 * @author wangzihao
 * 2020/05/30/019
 */
public class RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> extends CompletableFuture<COMPLETE_RESULT> {
    private final Collection<Consumer<CHUNK>> chunkConsumerList = new LinkedList<>();
    private final Collection<BiConsumer<CHUNK, Integer>> chunkIndexConsumerList = new LinkedList<>();
    private boolean lockCallbackMethod = false;
    private Executor chunkScheduler;

    RpcClientCompletableFuture(RpcClientReactivePublisher source) {
        source.subscribe(new SubscriberAdapter(this));
    }

    /**
     * 是执行回调方法时上同步锁, false可以保证回调通知的先后顺序. 为true则无法并行回调通知了
     *
     * @param lockCallbackMethod 是否锁回调方法
     * @return this
     */
    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> lockCallbackMethod(boolean lockCallbackMethod) {
        this.lockCallbackMethod = lockCallbackMethod;
        return this;
    }

    /**
     * 收到chunk数据时用的线程池 (callback默认是执行在IO线程,阻塞IO线程的话,会导致超时)
     *
     * @param chunkScheduler 异步调度器
     * @return this
     */
    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> chunkScheduler(Executor chunkScheduler) {
        this.chunkScheduler = chunkScheduler;
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(Consumer<CHUNK> consumer) {
        getChunkConsumerList().add(consumer);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(BiConsumer<CHUNK, Integer> consumer) {
        getChunkIndexConsumerList().add(consumer);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(Consumer<CHUNK> consumer, int onIndex) {
        getChunkIndexConsumerList().add((chunk, index) -> {
            if (index == onIndex) {
                consumer.accept(chunk);
            }
        });
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk1(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 0);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk2(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 1);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk3(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 2);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk4(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 3);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk5(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 4);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk6(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 5);
        return this;
    }

    public Collection<Consumer<CHUNK>> getChunkConsumerList() {
        return chunkConsumerList;
    }

    public Collection<BiConsumer<CHUNK, Integer>> getChunkIndexConsumerList() {
        return chunkIndexConsumerList;
    }

    public void callbackChunkConsumerList(CHUNK chunk, int index) {
        if (lockCallbackMethod) {
            synchronized (this) {
                Executor executor = this.chunkScheduler;
                if (executor != null) {
                    executor.execute(() -> callbackChunkConsumerList0(chunk, index));
                } else {
                    callbackChunkConsumerList0(chunk, index);
                }
            }
        } else {
            Executor executor = this.chunkScheduler;
            if (executor != null) {
                executor.execute(() -> callbackChunkConsumerList0(chunk, index));
            } else {
                callbackChunkConsumerList0(chunk, index);
            }
        }
    }

    public void callbackChunkConsumerList0(CHUNK chunk, int index) {
        for (Consumer<CHUNK> chunkConsumer : getChunkConsumerList()) {
            chunkConsumer.accept(chunk);
        }
        for (BiConsumer<CHUNK, Integer> chunkConsumer : getChunkIndexConsumerList()) {
            chunkConsumer.accept(chunk, index);
        }
    }

    public static class SubscriberAdapter<RESULT, CHUNK> implements Subscriber<RESULT>, ChunkListener<CHUNK> {
        private final RpcClientCompletableFuture<RESULT, CHUNK> completableFuture;
        private final AtomicInteger chunkIndex = new AtomicInteger();
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
            completableFuture.callbackChunkConsumerList(chunk, chunkIndex.getAndIncrement());
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
