package com.github.netty.protocol.nrpc.service;

/**
 * RpcCommandServiceImpl
 * @author wangzihao
 * 2018/8/20/020
 */
public class RpcCommandServiceImpl implements RpcCommandService {

    @Override
    public byte[] ping() {
        return "ok".getBytes();
    }

}
