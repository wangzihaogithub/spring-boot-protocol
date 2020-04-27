package com.github.netty.nrpc.client;

import com.github.netty.nrpc.api.HelloService;
import com.github.netty.springboot.NettyRpcClient;

@NettyRpcClient(serviceImplName = "nrpc-server",timeout = 100)
public interface HelloClient extends HelloService {

}
