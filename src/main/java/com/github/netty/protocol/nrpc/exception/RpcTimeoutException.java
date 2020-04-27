package com.github.netty.protocol.nrpc.exception;

/**
 * RpcTimeoutException
 * @author wangzihao
 *  2018/8/20/020
 */
public class RpcTimeoutException extends RpcException {
    private long createTimestamp;
    private long expiryTimestamp;
    private long timestamp = System.currentTimeMillis();
    public RpcTimeoutException(String message,boolean writableStackTrace,long createTimestamp,long expiryTimestamp) {
        super(message, null, false, writableStackTrace);
        this.createTimestamp = createTimestamp;
        this.expiryTimestamp = expiryTimestamp;
    }

    public long getCreateTimestamp() {
        return createTimestamp;
    }

    public void setCreateTimestamp(long createTimestamp) {
        this.createTimestamp = createTimestamp;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(long expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
