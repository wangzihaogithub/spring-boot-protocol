package com.github.netty.protocol.nrpc;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;

public class RpcEmitter<RESULT, CHUNK> implements Emitter<RESULT, CHUNK> {
    private Queue<Object> chunkList = new LinkedList<>();
    private Object lastResult;
    private volatile BiConsumer<Object, State> sendHandler;

    @Override
    public void send(CHUNK chunk) {
        if (chunk == null) {
            throw new NullPointerException("send null chunk!");
        }
        if (sendHandler == null) {
            synchronized (this) {
                if (sendHandler == null) {
                    chunkList.add(chunk);
                } else {
                    sendHandler.accept(chunk, RpcContext.RpcState.WRITE_CHUNK);
                }
            }
        } else {
            sendHandler.accept(chunk, RpcContext.RpcState.WRITE_CHUNK);
        }
    }

    @Override
    public void complete(RESULT lastResult) {
        if (sendHandler == null) {
            synchronized (this) {
                if (sendHandler == null) {
                    this.lastResult = lastResult;
                } else {
                    sendHandler.accept(lastResult, RpcContext.RpcState.WRITE_FINISH);
                }
            }
        } else {
            sendHandler.accept(lastResult, RpcContext.RpcState.WRITE_FINISH);
        }
    }

    @Override
    public void setSendHandler(BiConsumer<Object, State> sendHandler) {
        synchronized (this) {
            Object chunk;
            while (null != (chunk = chunkList.poll())) {
                sendHandler.accept(chunk, RpcContext.RpcState.WRITE_CHUNK);
            }
            this.chunkList = null;
            if (lastResult != null) {
                sendHandler.accept(lastResult, RpcContext.RpcState.WRITE_FINISH);
                this.lastResult = null;
            }
            this.sendHandler = sendHandler;
        }
    }
}
