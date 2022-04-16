package com.github.netty.nrpc.server.example;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.protocol.NRpcProtocol;
import com.github.netty.protocol.nrpc.RpcContext;
import com.github.netty.protocol.nrpc.RpcServerChannelHandler;
import com.github.netty.protocol.nrpc.RpcServerInstance;
import com.github.netty.protocol.nrpc.State;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * rpc call Lifecycle Management
 * @author wangzihao 2020-04-24
 */
@Component
public class RpcServerAop implements com.github.netty.protocol.nrpc.RpcServerAop {

    @Override
    public void onInitAfter(NRpcProtocol protocol) {

    }

    @Override
    public void onConnectAfter(RpcServerChannelHandler channel) {

    }

    @Override
    public void onDisconnectAfter(RpcServerChannelHandler channel) {

    }

    @Override
    public void onDecodeRequestBefore(RpcContext<RpcServerInstance> rpcContext, Map<String, Object> params) {

    }

    @Override
    public void onTimeout(RpcContext<RpcServerInstance> rpcContext) {

    }

    @Override
    public void onResponseAfter(RpcContext<RpcServerInstance> rpcContext) {

    }

    @Override
    public void onStateUpdate(RpcContext<RpcServerInstance> rpcContext, State formState, State toState) {
        LoggerFactoryX.getLogger(getClass()).info("requestId = {}, form = {}, to = {}",rpcContext.getRequest().getRequestId(),formState,toState);
    }
}
