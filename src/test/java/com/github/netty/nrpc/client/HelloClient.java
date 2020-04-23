package com.github.netty.nrpc.client;

import com.github.netty.nrpc.api.NRpcHelloService;
import com.github.netty.springboot.NettyRpcClient;

@NettyRpcClient(serviceImplName = "nrpc-server")
public interface HelloClient extends NRpcHelloService {

}
