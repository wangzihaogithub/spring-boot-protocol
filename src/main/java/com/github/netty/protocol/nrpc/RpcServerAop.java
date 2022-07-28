package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.NRpcProtocol;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;

/**
 * event aop
 *
 * @author wangzihao
 */
public interface RpcServerAop {

    FastThreadLocal<RpcContext<RpcServerInstance>> CONTEXT_LOCAL = new FastThreadLocal<>();

    default void onInitAfter(NRpcProtocol protocol) {
    }

    default void onConnectAfter(RpcServerChannelHandler channel) {
    }

    default void onDisconnectAfter(RpcServerChannelHandler channel) {
    }

    default void onDecodeRequestBefore(RpcContext<RpcServerInstance> rpcContext, Map<String, Object> params) {
    }

    default void onChunkAfter(RpcContext<RpcServerInstance> rpcContext, Object chunk, int chunkIndex, int chunkId, RpcEmitter emitter) {
    }

    default void onResponseAfter(RpcContext<RpcServerInstance> rpcContext) {
    }

    default void onTimeout(RpcContext<RpcServerInstance> rpcContext) {
    }

    default void onStateUpdate(RpcContext<RpcServerInstance> rpcContext, State formState, State toState) {
    }
}
