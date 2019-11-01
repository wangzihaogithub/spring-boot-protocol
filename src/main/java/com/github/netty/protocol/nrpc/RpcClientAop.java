package com.github.netty.protocol.nrpc;

import io.netty.util.concurrent.FastThreadLocal;

/**
 * event aop
 * @author wangzihao
 */
public interface RpcClientAop {

    default void onInitAfter(RpcClient rpcClient){}
    default void onConnectAfter(RpcClient rpcClient){}
    default void onDisconnectAfter(RpcClient rpcClient){}
    default void onResponseAfter(RpcContext<RpcClient> rpcContext){}

    FastThreadLocal<RpcContext<RpcClient>> CONTEXT_LOCAL = new FastThreadLocal<RpcContext<RpcClient>>(){
        @Override
        protected RpcContext<RpcClient> initialValue() throws Exception {
            return new RpcContext<>();
        }

        @Override
        protected void onRemoval(RpcContext<RpcClient> value) throws Exception {
            value.recycle();
        }
    };
}
