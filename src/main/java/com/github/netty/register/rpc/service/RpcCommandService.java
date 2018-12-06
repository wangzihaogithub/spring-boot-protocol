package com.github.netty.register.rpc.service;


import com.github.netty.annotation.RegisterFor;

/**
 * rpc命令服务
 * @author acer01
 * 2018/8/20/020
 */
@RegisterFor.RpcService(value = "/hrpc/command",timeout = 1000 * 10)
public interface RpcCommandService {

    /**
     * ping
     * @return
     */
    byte[] ping();

}
