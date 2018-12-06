package com.github.netty.register.rpc.exception;

/**
 * 连接异常
 * @author acer01
 * 2018/8/21/021
 */
public class RpcConnectException extends RpcException {

    public RpcConnectException(String message) {
        super(message, null, false, false);
    }

    public RpcConnectException(String message, Throwable cause) {
        super(message, cause, false, false);
        if(cause != null) {
            setStackTrace(cause.getStackTrace());
        }
    }
}
