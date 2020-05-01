package com.github.netty.nrpc.client;

import com.github.netty.nrpc.api.HelloService;
import com.github.netty.springboot.NettyRpcClient;

@NettyRpcClient(serviceName = "nrpc-server",timeout = 300)
public interface HelloClient extends HelloService {

}
