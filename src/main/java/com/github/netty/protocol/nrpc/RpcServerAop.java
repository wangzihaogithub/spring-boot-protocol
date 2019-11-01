package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.NRpcProtocol;
import io.netty.util.concurrent.FastThreadLocal;

/**
 * event aop
 * @author wangzihao
 */
public interface RpcServerAop {

    default void onInitAfter(NRpcProtocol protocol){}
    default void onConnectAfter(RpcServerChannelHandler channel){}
    default void onDisconnectAfter(RpcServerChannelHandler channel){}
    default void onResponseAfter(RpcContext<RpcServerInstance> rpcContext){}

    FastThreadLocal<RpcContext<RpcServerInstance>> CONTEXT_LOCAL = new FastThreadLocal<RpcContext<RpcServerInstance>>(){
        @Override
        protected RpcContext<RpcServerInstance> initialValue() throws Exception {
            return new RpcContext<>();
        }

        @Override
        protected void onRemoval(RpcContext<RpcServerInstance> value) throws Exception {
            value.recycle();
        }
    };
}
