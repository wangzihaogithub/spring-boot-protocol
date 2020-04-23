package com.github.netty.protocol.nrpc.service;

import com.github.netty.annotation.Protocol;
import org.reactivestreams.Publisher;

/**
 * RpcCommandAsyncService
 * @author wangzihao
 * 2020/4/23/020
 */
@Protocol.RpcService(value = "/hrpc/command")
public interface RpcCommandAsyncService {

    Publisher<byte[]> ping();
}
