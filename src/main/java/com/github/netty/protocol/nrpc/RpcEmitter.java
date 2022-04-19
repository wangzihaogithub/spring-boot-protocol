package com.github.netty.protocol.nrpc;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

public class RpcEmitter<RESULT, CHUNK> implements Emitter<RESULT, CHUNK> {
    private final Queue<Object> earlyChunkList = new LinkedList<>();
    private Object earlyCompleteResult;
    private volatile boolean usable;

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
    public <T> CompletableFuture<T> send(Object chunk, Class<T> type, int timeout) {
        if (chunk == null) {
            throw new NullPointerException("send null chunk!");
        }
        if (usable) {
            return writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, new RpcServerChannelHandler.ChunkAckCallback<>(), type, timeout);
        } else {
            synchronized (this) {
                if (usable) {
                    return writeAndFlush(chunk, RpcContext.RpcState.WRITE_CHUNK, new RpcServerChannelHandler.ChunkAckCallback<>(), type, timeout);
                } else {
                    ChunkAckPacket<T> packet = new ChunkAckPacket<>(chunk, type, timeout);
                    earlyChunkList.add(packet);
                    return packet.ackCallback;
                }
            }
        }
    }

    @Override
    public void complete(RESULT completeResult) {
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
    }

    public void usable(RpcPacket.RequestPacket request,
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
