package com.github.netty.register.rpc.exception;

/**
 * RPC 异常响应
 *
 * @author acer01
 *  2018/8/21/021
 */
public class RpcResponseException extends RpcException {

    /**
     * 错误状态码
     */
    private int status;

    public RpcResponseException(int status,String message) {
        super(message, null, false, false);
        this.status = status;
    }

    public RpcResponseException(int status,String message,boolean writableStackTrace) {
        super(message, null, false, writableStackTrace);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
