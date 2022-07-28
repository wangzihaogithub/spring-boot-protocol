package com.github.netty.protocol.nrpc.service;

import com.github.netty.annotation.NRpcService;
import org.reactivestreams.Publisher;

/**
 * RpcCommandAsyncService
 *
 * @author wangzihao
 * 2020/4/23/020
 */
@NRpcService(value = "/_nrpc/command", timeout = 600)
public interface RpcCommandAsyncService {

    Publisher<byte[]> ping();
}
