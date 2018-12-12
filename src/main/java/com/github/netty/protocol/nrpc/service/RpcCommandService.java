package com.github.netty.protocol.nrpc.service;


import com.github.netty.annotation.Protocol;

/**
 * rpc命令服务
 * @author acer01
 * 2018/8/20/020
 */
@Protocol.RpcService(value = "/hrpc/command",timeout = 1000 * 10)
public interface RpcCommandService {

    /**
     * ping
     * @return
     */
    byte[] ping();

}
