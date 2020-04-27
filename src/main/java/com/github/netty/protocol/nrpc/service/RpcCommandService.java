package com.github.netty.protocol.nrpc.service;


import com.github.netty.annotation.Protocol;

/**
 * RpcCommandService
 * @author wangzihao
 * 2018/8/20/020
 */
@Protocol.RpcService(value = "/_nrpc/command",timeout = 600)
public interface RpcCommandService {

    /**
     * ping
     * @return byte[]
     */
    byte[] ping();

}
