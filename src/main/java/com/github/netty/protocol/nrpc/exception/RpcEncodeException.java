package com.github.netty.protocol.nrpc.exception;

/**
 * RpcEncodeException
 * @author wangzihao
 *  2018/8/20/020
 */
public class RpcEncodeException extends RpcException {

    public RpcEncodeException(String message) {
        this(message, null);
    }

    public RpcEncodeException(String message, Throwable cause) {
        super(message, cause, false, false);
        if(cause != null) {
            setStackTrace(cause.getStackTrace());
        }
    }


}
