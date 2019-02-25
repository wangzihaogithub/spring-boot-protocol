package com.github.netty.protocol.nrpc.service;

/**
 * rpc命令服务
 * @author wangzihao
 * 2018/8/20/020
 */
public class RpcCommandServiceImpl implements RpcCommandService {

    @Override
    public byte[] ping() {
        return "ok".getBytes();
    }

}
