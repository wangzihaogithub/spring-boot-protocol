package com.github.netty.protocol.nrpc;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcEmitter<RESULT, CHUNK> implements Emitter<RESULT, CHUNK> {
    private final Queue<Object> earlyChunkList = new LinkedList<>();
    private Object earlyCompleteResult;
    private volatile boolean usable;
    private final AtomicBoolean completeFlag = new AtomicBoolean();
    private final AtomicInteger sendCount = new AtomicInteger();

    RpcPacket.RequestPacket request;
    RpcPacket.ResponseLastPacket lastResponse;
    RpcContext<RpcServerInstance> rpcContext;
    RpcServerChannelHandler channelHandler;
    RpcMethod<RpcServerInstance> rpcMethod;
    RpcServerChannelHandler.RpcRunnable rpcRunnable;

    @Override
    public void send(CHUNK chunk) {
        if (chunk == null) {
            throw new NullPointerException("send null chunk!");
        }
        sendCount.incrementAndGet();
        if (usable) {
            writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, null);
        } else {
            synchronized (this) {
                if (usable) {
                    writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, null);
                } else {
                    earlyChunkList.add(chunk);
                }
            }
        }
    }

    @Override
    public <T> CompletableFuture<T> send(CHUNK chunk, Class<T> responseType, int responseTimeout) {
        if (chunk == null) {
            throw new NullPointerException("send null chunk!");
        }
        if (isComplete()) {
            throw new IllegalStateException("current complete state. can not send!");
        }
        if (usable) {
            return writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, new RpcServerChannelHandler.ChunkAckCallback<>(), responseType, responseTimeout);
        } else {
            synchronized (this) {
                if (usable) {
                    return writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, new RpcServerChannelHandler.ChunkAckCallback<>(), responseType, responseTimeout);
                } else {
                    ChunkAckPacket<T> packet = new ChunkAckPacket<>(chunk, responseType, responseTimeout);
                    earlyChunkList.add(packet);
                    return packet.ackCallback;
                }
            }
        }
    }

    @Override
    public boolean complete(RESULT completeResult) {
        return complete0(completeResult);
    }

    @Override
    public boolean complete(Throwable throwable) {
        return complete0(throwable);
    }

    private boolean complete0(Object completeResult) {
        if (!completeFlag.compareAndSet(false, true)) {
            return false;
        }
        if (usable) {
            writeAndFlush(completeResult, RpcContext.RpcState.WRITE_FINISH, null);
        } else {
            synchronized (this) {
                if (usable) {
                    writeAndFlush(completeResult, RpcContext.RpcState.WRITE_FINISH, null);
                } else {
                    this.earlyCompleteResult = completeResult;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isComplete() {
        return completeFlag.get();
    }

    @Override
    public int getSendCount() {
        return sendCount.get();
    }

    void usable(RpcPacket.RequestPacket request,
                RpcPacket.ResponseLastPacket lastResponse,
                RpcContext<RpcServerInstance> rpcContext,
                RpcServerChannelHandler channelHandler,
                RpcMethod<RpcServerInstance> rpcMethod,
                RpcServerChannelHandler.RpcRunnable rpcRunnable) {
        this.request = request;
        this.lastResponse = lastResponse;
        this.rpcContext = rpcContext;
        this.channelHandler = channelHandler;
        this.rpcMethod = rpcMethod;
        this.rpcRunnable = rpcRunnable;
        synchronized (this) {
            Object chunk;
            while (null != (chunk = earlyChunkList.poll())) {
                if (chunk instanceof ChunkAckPacket) {
                    ChunkAckPacket<Object> packet = (ChunkAckPacket) chunk;
                    writeAndFlush(packet.data, RpcContext.RpcState.WRITE_CHUNK, packet.ackCallback, packet.type, packet.timeout);
                } else {
                    writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, null);
                }
            }
            if (earlyCompleteResult != null) {
                writeAndFlush(earlyCompleteResult, RpcContext.RpcState.WRITE_FINISH, null);
                this.earlyCompleteResult = null;
            }
            this.usable = true;
        }
    }

    static class ChunkAckPacket<T> {
        Object data;
        Class<T> type;
        int timeout;
        final RpcServerChannelHandler.ChunkAckCallback<T> ackCallback = new RpcServerChannelHandler.ChunkAckCallback<>();

        ChunkAckPacket(Object data, Class<T> type, int timeout) {
            this.data = data;
            this.type = type;
            this.timeout = timeout;
        }
    }


    protected void writeAndFlush(Object data, State state, RpcServerChannelHandler.ChunkAckCallback ackCallback) {
        RpcServerChannelHandler.buildAndWriteAndFlush(request, lastResponse, rpcContext, channelHandler, rpcMethod, data, null, state, ackCallback, rpcRunnable);
    }

    protected <T> RpcServerChannelHandler.ChunkAckCallback<T> writeAndFlush(Object data, State state, RpcServerChannelHandler.ChunkAckCallback<T> ackCallback, Class<T> type, int timeout) {
        ackCallback.type = type;
        ackCallback.emitter = this;
        ackCallback.timeout = timeout;
        ackCallback.executor = channelHandler.getExecutor();
        if (ackCallback.executor == null) {
            ackCallback.executor = channelHandler.getContext().executor();
        }
        writeAndFlush(data, state, ackCallback);
        return ackCallback;
    }
}
