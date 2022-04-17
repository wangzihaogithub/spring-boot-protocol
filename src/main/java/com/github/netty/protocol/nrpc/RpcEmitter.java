package com.github.netty.protocol.nrpc;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;

public class RpcEmitter<RESULT, CHUNK> implements Emitter<RESULT, CHUNK> {
    private final Queue<Object> earlyChunkList = new LinkedList<>();
    private Object earlyCompleteResult;
    private volatile BiConsumer<Object, State> sendHandler;

    @Override
    public void send(CHUNK chunk) {
        if (chunk == null) {
            throw new NullPointerException("send null chunk!");
        }
        if (sendHandler == null) {
            synchronized (this) {
                if (sendHandler == null) {
                    earlyChunkList.add(chunk);
                } else {
                    sendHandler.accept(chunk, RpcContext.RpcState.WRITE_CHUNK);
                }
            }
        } else {
            sendHandler.accept(chunk, RpcContext.RpcState.WRITE_CHUNK);
        }
    }

    @Override
    public void complete(RESULT completeResult) {
        if (sendHandler == null) {
            synchronized (this) {
                if (sendHandler == null) {
                    this.earlyCompleteResult = completeResult;
                } else {
                    sendHandler.accept(completeResult, RpcContext.RpcState.WRITE_FINISH);
                }
            }
        } else {
            sendHandler.accept(completeResult, RpcContext.RpcState.WRITE_FINISH);
        }
    }

    protected void setSendHandler(BiConsumer<Object, State> sendHandler) {
        synchronized (this) {
            Object chunk;
            while (null != (chunk = earlyChunkList.poll())) {
                sendHandler.accept(chunk, RpcContext.RpcState.WRITE_CHUNK);
            }
            if (earlyCompleteResult != null) {
                sendHandler.accept(earlyCompleteResult, RpcContext.RpcState.WRITE_FINISH);
                this.earlyCompleteResult = null;
            }
            this.sendHandler = sendHandler;
        }
    }
}
