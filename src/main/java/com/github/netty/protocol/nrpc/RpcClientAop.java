package com.github.netty.protocol.nrpc;

import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;

/**
 * event aop
 * @author wangzihao
 */
public interface RpcClientAop {

    default void onInitAfter(RpcClient rpcClient){}
    default void onConnectAfter(RpcClient rpcClient){}
    default void onDisconnectAfter(RpcClient rpcClient){}
    default void onEncodeRequestBefore(RpcContext<RpcClient> rpcContext, Map<String,Object> params){}
    default void onTimeout(RpcContext<RpcClient> rpcContext){}
    default void onResponseAfter(RpcContext<RpcClient> rpcContext){}
    default void onStateUpdate(RpcContext<RpcClient> rpcContext){}

    FastThreadLocal<RpcContext<RpcClient>> CONTEXT_LOCAL = new FastThreadLocal<>();
}
