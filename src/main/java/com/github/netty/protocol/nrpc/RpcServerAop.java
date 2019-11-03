package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.NRpcProtocol;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;

/**
 * event aop
 * @author wangzihao
 */
public interface RpcServerAop {

    default void onInitAfter(NRpcProtocol protocol){}
    default void onConnectAfter(RpcServerChannelHandler channel){}
    default void onDisconnectAfter(RpcServerChannelHandler channel){}
    default void onDecodeRequestBefore(RpcContext<RpcServerInstance> rpcContext, Map<String,Object> params){}
    default void onResponseAfter(RpcContext<RpcServerInstance> rpcContext){}

    FastThreadLocal<RpcContext<RpcServerInstance>> CONTEXT_LOCAL = new FastThreadLocal<>();
}
