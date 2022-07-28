package com.github.netty.protocol.nrpc;

import io.netty.util.concurrent.GlobalEventExecutor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import static com.github.netty.protocol.nrpc.RpcClientAop.CONTEXT_LOCAL;

/**
 * support CompletableFuture async response.
 *
 * @author wangzihao
 * 2020/05/30/019
 */
public class RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> extends CompletableFuture<COMPLETE_RESULT> {
    private final Collection<Consumer<CHUNK>> chunkConsumerList = new ConcurrentLinkedQueue<>();
    private final Collection<BiConsumer<CHUNK, Integer>> chunkIndexConsumerList = new ConcurrentLinkedQueue<>();
    private final Collection<Consumer3<CHUNK, Integer, ChunkAck>> chunkIndexAckConsumerList = new ConcurrentLinkedQueue<>();
    private final RpcMethod<RpcClient> rpcMethod;
    private final AtomicBoolean chunkBuildEndFlag = new AtomicBoolean();
    private Executor chunkScheduler;
    private Subscription subscription;

    RpcClientChunkCompletableFuture(RpcMethod<RpcClient> rpcMethod, RpcClientReactivePublisher source) {
        this.rpcMethod = rpcMethod;
        source.subscribe(new SubscriberAdapter(this));
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> request() {
        subscription.request(1);
        return this;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (subscription != null) {
            subscription.cancel();
        }
        return super.cancel(mayInterruptIfRunning);
    }

    private void chunkBuildEnd() {
        if (chunkBuildEndFlag.compareAndSet(false, true)) {
            request();
        }
    }

    /**
     * 收到chunk数据时用的线程池 (callback默认是执行在IO线程,阻塞IO线程的话,会导致超时)
     *
     * @param chunkScheduler 异步调度器
     * @return this
     */
    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> chunkScheduler(Executor chunkScheduler) {
        this.chunkScheduler = chunkScheduler;
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(Consumer<CHUNK> consumer) {
        getChunkConsumerList().add(consumer);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(BiConsumer<CHUNK, Integer> consumer) {
        getChunkIndexConsumerList().add(consumer);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk(Consumer<CHUNK> consumer, int onIndex) {
        getChunkIndexConsumerList().add((chunk, index) -> {
            if (index == onIndex) {
                consumer.accept(chunk);
            }
        });
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk1(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 0);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk2(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 1);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk3(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 2);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk4(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 3);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk5(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 4);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk6(Consumer<CHUNK> consumer) {
        whenChunk(consumer, 5);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunkAck(Consumer3<CHUNK, Integer, ChunkAck> consumer) {
        getChunkIndexAckConsumerList().add(consumer);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunkAck(BiConsumer<CHUNK, ChunkAck> consumer, int onIndex) {
        getChunkIndexAckConsumerList().add((chunk, index, ack) -> {
            if (index == onIndex) {
                consumer.accept(chunk, ack);
            }
        });
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk1Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 0);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk2Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 1);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk3Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 2);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk4Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 3);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk5Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 4);
        return this;
    }

    public RpcClientChunkCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk6Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 5);
        return this;
    }

    public Collection<Consumer<CHUNK>> getChunkConsumerList() {
        return chunkConsumerList;
    }

    public Collection<BiConsumer<CHUNK, Integer>> getChunkIndexConsumerList() {
        return chunkIndexConsumerList;
    }

    public Collection<Consumer3<CHUNK, Integer, ChunkAck>> getChunkIndexAckConsumerList() {
        return chunkIndexAckConsumerList;
    }

    public void callbackChunkConsumerList(CHUNK chunk, int index, int chunkId, ChunkAck ack) {
        if (!existChunkCallback()) {
            ack.ack();
            return;
        }
        Executor chunkScheduler = this.chunkScheduler;
        if (chunkScheduler == null) {
            chunkScheduler = GlobalEventExecutor.INSTANCE;
        }
        RpcContext<RpcClient> rpcContext = CONTEXT_LOCAL.get();
        chunkScheduler.execute(() -> {
            CONTEXT_LOCAL.set(rpcContext);
            try {
                // 1.chunk
                for (Consumer<CHUNK> chunkConsumer : getChunkConsumerList()) {
                    try {
                        chunkConsumer.accept(chunk);
                    } catch (Exception e) {
                        rpcMethod.getLog().warn(rpcMethod + " chunkConsumer(chunk) exception = {}", e.toString(), e);
                    }
                }
                // 2.chunk,index
                for (BiConsumer<CHUNK, Integer> chunkConsumer : getChunkIndexConsumerList()) {
                    try {
                        chunkConsumer.accept(chunk, index);
                    } catch (Exception e) {
                        rpcMethod.getLog().warn(rpcMethod + " chunkConsumer(chunk,index) exception = {}", e.toString(), e);
                    }
                }
                // 3.chunk,index,ack
                for (Consumer3<CHUNK, Integer, ChunkAck> chunkConsumer : getChunkIndexAckConsumerList()) {
                    try {
                        chunkConsumer.accept(chunk, index, ack);
                    } catch (Exception e) {
                        rpcMethod.getLog().warn(rpcMethod + " chunkConsumer(chunk,index,ack) exception = {}", e.toString(), e);
                    }
                }
            } finally {
                Supplier<Object> chunkSupplier = () -> chunk;
                // call aop
                for (RpcClientAop aop : rpcMethod.getInstance().getAopList()) {
                    try {
                        aop.onChunkAfter(rpcContext, chunkSupplier, index, chunkId, ack);
                    } catch (Exception e) {
                        rpcMethod.getLog().warn(rpcMethod + " client.aop.onChunkAfter() exception = {}", e.toString(), e);
                    }
                }
                // ensure ack
                if (!ack.isAck()) {
                    ack.ack();
                }
                CONTEXT_LOCAL.remove();
            }
        });
    }

    public boolean existChunkCallback() {
        if (!getChunkConsumerList().isEmpty()) {
            return true;
        }
        if (!getChunkIndexConsumerList().isEmpty()) {
            return true;
        }
        return !getChunkIndexAckConsumerList().isEmpty();
    }

    @Override
    public CompletableFuture<COMPLETE_RESULT> whenComplete(BiConsumer<? super COMPLETE_RESULT, ? super Throwable> action) {
        chunkBuildEnd();
        return super.whenComplete(action);
    }

    @Override
    public CompletableFuture<COMPLETE_RESULT> whenCompleteAsync(BiConsumer<? super COMPLETE_RESULT, ? super Throwable> action) {
        chunkBuildEnd();
        return super.whenCompleteAsync(action);
    }

    @Override
    public <U> CompletableFuture<U> thenApply(Function<? super COMPLETE_RESULT, ? extends U> fn) {
        chunkBuildEnd();
        return super.thenApply(fn);
    }

    @Override
    public COMPLETE_RESULT get() throws InterruptedException, ExecutionException {
        chunkBuildEnd();
        return super.get();
    }

    @Override
    public COMPLETE_RESULT get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        chunkBuildEnd();
        return super.get(timeout, unit);
    }

    @Override
    public COMPLETE_RESULT join() {
        chunkBuildEnd();
        return super.join();
    }

    @Override
    public COMPLETE_RESULT getNow(COMPLETE_RESULT valueIfAbsent) {
        chunkBuildEnd();
        return super.getNow(valueIfAbsent);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super COMPLETE_RESULT, ? extends U> fn) {
        chunkBuildEnd();
        return super.thenApplyAsync(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super COMPLETE_RESULT, ? extends U> fn, Executor executor) {
        chunkBuildEnd();
        return super.thenApplyAsync(fn, executor);
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super COMPLETE_RESULT> action) {
        chunkBuildEnd();
        return super.thenAccept(action);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super COMPLETE_RESULT> action) {
        chunkBuildEnd();
        return super.thenAcceptAsync(action);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super COMPLETE_RESULT> action, Executor executor) {
        chunkBuildEnd();
        return super.thenAcceptAsync(action, executor);
    }

    @Override
    public CompletableFuture<Void> thenRun(Runnable action) {
        chunkBuildEnd();
        return super.thenRun(action);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        chunkBuildEnd();
        return super.thenRunAsync(action);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        chunkBuildEnd();
        return super.thenRunAsync(action, executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super COMPLETE_RESULT, ? super U, ? extends V> fn) {
        chunkBuildEnd();
        return super.thenCombine(other, fn);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super COMPLETE_RESULT, ? super U, ? extends V> fn) {
        chunkBuildEnd();
        return super.thenCombineAsync(other, fn);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super COMPLETE_RESULT, ? super U, ? extends V> fn, Executor executor) {
        chunkBuildEnd();
        return super.thenCombineAsync(other, fn, executor);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super COMPLETE_RESULT, ? super U> action) {
        chunkBuildEnd();
        return super.thenAcceptBoth(other, action);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super COMPLETE_RESULT, ? super U> action) {
        chunkBuildEnd();
        return super.thenAcceptBothAsync(other, action);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super COMPLETE_RESULT, ? super U> action, Executor executor) {
        chunkBuildEnd();
        return super.thenAcceptBothAsync(other, action, executor);
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        chunkBuildEnd();
        return super.runAfterBoth(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        chunkBuildEnd();
        return super.runAfterBothAsync(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        chunkBuildEnd();
        return super.runAfterBothAsync(other, action, executor);
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends COMPLETE_RESULT> other, Function<? super COMPLETE_RESULT, U> fn) {
        chunkBuildEnd();
        return super.applyToEither(other, fn);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends COMPLETE_RESULT> other, Function<? super COMPLETE_RESULT, U> fn) {
        chunkBuildEnd();
        return super.applyToEitherAsync(other, fn);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends COMPLETE_RESULT> other, Function<? super COMPLETE_RESULT, U> fn, Executor executor) {
        chunkBuildEnd();
        return super.applyToEitherAsync(other, fn, executor);
    }

    @Override
    public CompletableFuture<Void> acceptEither(CompletionStage<? extends COMPLETE_RESULT> other, Consumer<? super COMPLETE_RESULT> action) {
        chunkBuildEnd();
        return super.acceptEither(other, action);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends COMPLETE_RESULT> other, Consumer<? super COMPLETE_RESULT> action) {
        chunkBuildEnd();
        return super.acceptEitherAsync(other, action);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends COMPLETE_RESULT> other, Consumer<? super COMPLETE_RESULT> action, Executor executor) {
        chunkBuildEnd();
        return super.acceptEitherAsync(other, action, executor);
    }

    @Override
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        chunkBuildEnd();
        return super.runAfterEither(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        chunkBuildEnd();
        return super.runAfterEitherAsync(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        chunkBuildEnd();
        return super.runAfterEitherAsync(other, action, executor);
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(Function<? super COMPLETE_RESULT, ? extends CompletionStage<U>> fn) {
        chunkBuildEnd();
        return super.thenCompose(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super COMPLETE_RESULT, ? extends CompletionStage<U>> fn) {
        chunkBuildEnd();
        return super.thenComposeAsync(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super COMPLETE_RESULT, ? extends CompletionStage<U>> fn, Executor executor) {
        chunkBuildEnd();
        return super.thenComposeAsync(fn, executor);
    }

    @Override
    public CompletableFuture<COMPLETE_RESULT> whenCompleteAsync(BiConsumer<? super COMPLETE_RESULT, ? super Throwable> action, Executor executor) {
        chunkBuildEnd();
        return super.whenCompleteAsync(action, executor);
    }

    @Override
    public <U> CompletableFuture<U> handle(BiFunction<? super COMPLETE_RESULT, Throwable, ? extends U> fn) {
        chunkBuildEnd();
        return super.handle(fn);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super COMPLETE_RESULT, Throwable, ? extends U> fn) {
        chunkBuildEnd();
        return super.handleAsync(fn);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super COMPLETE_RESULT, Throwable, ? extends U> fn, Executor executor) {
        chunkBuildEnd();
        return super.handleAsync(fn, executor);
    }

    @Override
    public CompletableFuture<COMPLETE_RESULT> toCompletableFuture() {
        chunkBuildEnd();
        return super.toCompletableFuture();
    }

    @Override
    public CompletableFuture<COMPLETE_RESULT> exceptionally(Function<Throwable, ? extends COMPLETE_RESULT> fn) {
        chunkBuildEnd();
        return super.exceptionally(fn);
    }

    @Override
    public boolean isDone() {
        chunkBuildEnd();
        return super.isDone();
    }

    @Override
    public boolean isCancelled() {
        chunkBuildEnd();
        return super.isCancelled();
    }

    @Override
    public boolean isCompletedExceptionally() {
        chunkBuildEnd();
        return super.isCompletedExceptionally();
    }

    @FunctionalInterface
    public interface Consumer3<T1, T2, T3> {
        void accept(T1 t1, T2 t2, T3 t3);
    }

    public static class SubscriberAdapter<RESULT, CHUNK> implements Subscriber<RESULT>, RpcDone.ChunkListener<CHUNK> {
        private final RpcClientChunkCompletableFuture<RESULT, CHUNK> completableFuture;
        private final AtomicInteger chunkIndex = new AtomicInteger();
        private RESULT result;
        private Throwable throwable;

        private SubscriberAdapter(RpcClientChunkCompletableFuture<RESULT, CHUNK> completableFuture) {
            this.completableFuture = completableFuture;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.completableFuture.subscription = s;
        }

        @Override
        public void onChunk(CHUNK chunk, int chunkId, ChunkAck ack) {
            completableFuture.callbackChunkConsumerList(chunk, chunkIndex.getAndIncrement(), chunkId, ack);
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
