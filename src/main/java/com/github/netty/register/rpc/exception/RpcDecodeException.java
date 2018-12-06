package com.github.netty.register.rpc.exception;

/**
 * 解码异常
 *
 * @author acer01
 *  2018/8/20/020
 */
public class RpcDecodeException extends RpcException {

    public RpcDecodeException(String message) {
        this(message, null);
    }

    public RpcDecodeException(String message, Throwable cause) {
        super(message, cause, false, false);
        if(cause != null) {
            setStackTrace(cause.getStackTrace());
        }
    }


}
