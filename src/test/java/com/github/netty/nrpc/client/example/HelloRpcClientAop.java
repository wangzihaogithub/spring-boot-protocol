package com.github.netty.nrpc.client.example;

import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * rpc call Lifecycle Management
 * @author wangzihao 2020-04-24
 */
@Component
public class HelloRpcClientAop implements com.github.netty.protocol.nrpc.RpcClientAop {

    @Override
    public void onInitAfter(RpcClient rpcClient) {

    }

    @Override
    public void onConnectAfter(RpcClient rpcClient) {

    }

    @Override
    public void onDisconnectAfter(RpcClient rpcClient) {

    }

    @Override
    public void onEncodeRequestBefore(RpcContext<RpcClient> rpcContext, Map<String, Object> params) {

    }

    @Override
    public void onResponseAfter(RpcContext<RpcClient> rpcContext) {

    }

    @Override
    public void onTimeout(RpcContext<RpcClient> rpcContext) {

    }

    @Override
    public void onStateUpdate(RpcContext<RpcClient> rpcContext) {

    }
}
