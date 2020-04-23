package com.github.netty.nrpc.api;

import com.github.netty.annotation.Protocol;

@Protocol.RpcService("/nrpc/hello")
public interface NRpcHelloService {
    String sayHello(@Protocol.RpcParam("name") String name);

}
