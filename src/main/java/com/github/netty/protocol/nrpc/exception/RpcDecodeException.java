package com.github.netty.protocol.nrpc.exception;

/**
 * RpcDecodeException
 * @author wangzihao
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
