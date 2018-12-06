package com.github.netty.register.rpc.service;

/**
 * rpc命令服务
 * @author acer01
 * 2018/8/20/020
 */
public class RpcCommandServiceImpl implements RpcCommandService {

    @Override
    public byte[] ping() {
        return "ok".getBytes();
    }

}
