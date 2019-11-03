package com.github.netty.protocol.nrpc.exception;

/**
 * RpcWriteException
 * @author wangzihao
 *  2019/11/03/022
 */
public class RpcWriteException extends RpcException {

    public RpcWriteException(String message) {
        this(message, null);
    }

    public RpcWriteException(String message, Throwable cause) {
        super(message, cause, false, false);
        if(cause != null) {
            setStackTrace(cause.getStackTrace());
        }
    }

}
