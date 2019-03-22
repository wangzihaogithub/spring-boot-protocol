package com.github.netty.protocol.nrpc.exception;

import com.github.netty.protocol.nrpc.RpcResponseStatus;

/**
 * RpcResponseException
 * @author wangzihao
 *  2018/8/21/021
 */
public class RpcResponseException extends RpcException {
    /**
     * Error status code
     */
    private RpcResponseStatus status;

    public RpcResponseException(RpcResponseStatus status,String message) {
        super(message, null, false, false);
        this.status = status;
    }

    public RpcResponseException(RpcResponseStatus status, String message, boolean writableStackTrace) {
        super(message, null, false, writableStackTrace);
        this.status = status;
    }

    public RpcResponseStatus getStatus() {
        return status;
    }
}
