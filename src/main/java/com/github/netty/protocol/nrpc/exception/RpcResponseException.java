package com.github.netty.protocol.nrpc.exception;

/**
 * RpcResponseException
 * @author wangzihao
 *  2018/8/21/021
 */
public class RpcResponseException extends RpcException {
    /**
     * Error status code
     */
    private Integer status;

    public RpcResponseException(Integer status,String message) {
        super(message, null, false, false);
        this.status = status;
    }

    public RpcResponseException(Integer status, String message, boolean writableStackTrace) {
        super(message, null, false, writableStackTrace);
        this.status = status;
    }

    public Integer getStatus() {
        return status;
    }
}
