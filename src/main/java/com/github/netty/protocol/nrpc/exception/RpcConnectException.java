package com.github.netty.protocol.nrpc.exception;

/**
 * 连接异常
 * @author wangzihao
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
