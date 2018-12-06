package com.github.netty.register.rpc.exception;

/**
 * 超时异常
 *
 * @author acer01
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
