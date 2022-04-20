package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.nrpc.codec.DataCodec;
import io.netty.channel.ChannelHandlerContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private final Collection<Consumer<CHUNK>> chunkConsumerList = new ConcurrentLinkedQueue<>();
    private final Collection<BiConsumer<CHUNK, Integer>> chunkIndexConsumerList = new ConcurrentLinkedQueue<>();
    private final Collection<Consumer3<CHUNK, Integer, ChunkAck>> chunkIndexAckConsumerList = new ConcurrentLinkedQueue<>();
    private final DataCodec dataCodec;

    private boolean lockCallbackMethod = false;
    private Executor chunkScheduler;

    RpcClientCompletableFuture(DataCodec dataCodec, RpcClientReactivePublisher source) {
        source.subscribe(new SubscriberAdapter(this));
        this.dataCodec = dataCodec;
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

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunkAck(Consumer3<CHUNK, Integer, ChunkAck> consumer) {
        getChunkIndexAckConsumerList().add(consumer);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunkAck(BiConsumer<CHUNK, ChunkAck> consumer, int onIndex) {
        getChunkIndexAckConsumerList().add((chunk, index, ack) -> {
            if (index == onIndex) {
                consumer.accept(chunk, ack);
            }
        });
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk1Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 0);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk2Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 1);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk3Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 2);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk4Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 3);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk5Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
        whenChunkAck(consumer, 4);
        return this;
    }

    public RpcClientCompletableFuture<COMPLETE_RESULT, CHUNK> whenChunk6Ack(BiConsumer<CHUNK, ChunkAck> consumer) {
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

    public void callbackChunkConsumerList(CHUNK chunk, int index, RpcPacket.ResponseChunkPacket rpcResponse, ChannelHandlerContext ctx) {
        int chunkId = rpcResponse.getChunkId();
        int requestId = rpcResponse.getRequestId();
        if (lockCallbackMethod) {
            synchronized (this) {
                Executor executor = this.chunkScheduler;
                if (executor != null) {
                    executor.execute(() -> callbackChunkConsumerList0(chunk, index, requestId, chunkId, ctx));
                } else {
                    callbackChunkConsumerList0(chunk, index, requestId, chunkId, ctx);
                }
            }
        } else {
            Executor executor = this.chunkScheduler;
            if (executor != null) {
                executor.execute(() -> callbackChunkConsumerList0(chunk, index, requestId, chunkId, ctx));
            } else {
                callbackChunkConsumerList0(chunk, index, requestId, chunkId, ctx);
            }
        }
    }

    public void callbackChunkConsumerList0(CHUNK chunk, int index, int requestId, int chunkId, ChannelHandlerContext ctx) {
        for (Consumer<CHUNK> chunkConsumer : getChunkConsumerList()) {
            chunkConsumer.accept(chunk);
        }
        for (BiConsumer<CHUNK, Integer> chunkConsumer : getChunkIndexConsumerList()) {
            chunkConsumer.accept(chunk, index);
        }
        Collection<Consumer3<CHUNK, Integer, ChunkAck>> consumer3List = getChunkIndexAckConsumerList();
        if (!consumer3List.isEmpty()) {
            ChunkAck ack = result -> {
                RpcPacket.ResponseChunkAckPacket ackPacket = RpcPacket.ResponsePacket.newChunkAckPacket(requestId, chunkId);
                Object data;
                if (result instanceof Throwable) {
                    ackPacket.setStatus(RpcPacket.ResponsePacket.SERVER_ERROR);
                    ackPacket.setMessage(dataCodec.buildThrowableRpcMessage((Throwable) result));
                    data = null;
                } else {
                    data = result;
                }
                if (data instanceof byte[]) {
                    ackPacket.setData((byte[]) data);
                    ackPacket.setEncode(DataCodec.Encode.BINARY);
                } else {
                    ackPacket.setData(dataCodec.encodeChunkResponseData(data));
                    ackPacket.setEncode(DataCodec.Encode.APP);
                }
                return ctx.writeAndFlush(ackPacket);
            };
            for (Consumer3<CHUNK, Integer, ChunkAck> chunkConsumer : consumer3List) {
                chunkConsumer.accept(chunk, index, ack);
            }
        }
    }

    @FunctionalInterface
    public interface Consumer3<T1, T2, T3> {
        void accept(T1 t1, T2 t2, T3 t3);
    }

    public static class SubscriberAdapter<RESULT, CHUNK> implements Subscriber<RESULT>, RpcDone.ChunkListener<CHUNK> {
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
        public void onChunk(CHUNK chunk, RpcPacket.ResponseChunkPacket rpcResponse, ChannelHandlerContext ctx) {
            completableFuture.callbackChunkConsumerList(chunk, chunkIndex.getAndIncrement(), rpcResponse, ctx);
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
