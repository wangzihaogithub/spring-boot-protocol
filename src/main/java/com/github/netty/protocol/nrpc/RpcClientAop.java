package com.github.netty.protocol.nrpc;

import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;
import java.util.function.Supplier;

/**
 * event aop
 *
 * @author wangzihao
 */
public interface RpcClientAop {

    FastThreadLocal<RpcContext<RpcClient>> CONTEXT_LOCAL = new FastThreadLocal<>();

    default void onInitAfter(RpcClient rpcClient) {
    }

    default void onConnectAfter(RpcClient rpcClient) {
    }

    default void onDisconnectAfter(RpcClient rpcClient) {
    }

    default void onEncodeRequestBefore(RpcContext<RpcClient> rpcContext, Map<String, Object> params) {
    }

    default void onTimeout(RpcContext<RpcClient> rpcContext) {
    }

    default void onChunkAfter(RpcContext<RpcClient> rpcContext, Supplier<Object> chunk, int chunkIndex, int chunkId, ChunkAck ack) {
    }

    default void onResponseAfter(RpcContext<RpcClient> rpcContext) {
    }

    default void onStateUpdate(RpcContext<RpcClient> rpcContext, State formState, State toState) {
    }
}
