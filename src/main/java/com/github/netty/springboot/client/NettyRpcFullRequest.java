package com.github.netty.springboot.client;

import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcMethod;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Information about the RPC full request
 *
 * @author wangzihao 2020/4/26
 */
public interface NettyRpcFullRequest extends NettyRpcRequest {
    RpcClient getRpcClient();

    RpcClient.Sender getSender();

    RpcMethod<RpcClient> getRpcMethod();

    Map<String, RpcMethod<RpcClient>> getRpcMethodMap();

    InetSocketAddress getRemoteAddress();

    Object getResponse() throws Throwable;
}
