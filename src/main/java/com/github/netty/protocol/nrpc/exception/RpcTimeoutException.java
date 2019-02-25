package com.github.netty.protocol.nrpc.exception;

/**
 * 超时异常
 *
 * @author wangzihao
 *  2018/8/20/020
 */
public class RpcTimeoutException extends RpcException {

    public RpcTimeoutException(String message) {
        super(message, null, false, false);
    }

    public RpcTimeoutException(String message,boolean writableStackTrace) {
        super(message, null, false, writableStackTrace);
    }

}
