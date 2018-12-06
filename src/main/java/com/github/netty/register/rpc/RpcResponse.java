package com.github.netty.register.rpc;

/**
 * RPC响应
 * @author 84215
 */
public class RpcResponse {

    private long requestId;
    private Integer status;
    private String message;
    private Integer encode;
    private byte[] data;

    //正常返回
    public static final int OK = 200;
    //找不到方法
    public static final int NO_SUCH_METHOD = 400;
    //找不到服务
    public static final int NO_SUCH_SERVICE = 401;
    //服务器错误
    public static final int SERVER_ERROR = 500;

    public static final int ENCODE_YES = 1;
    public static final int ENCODE_NO = 0;

    public RpcResponse() {
    }

    public RpcResponse(long requestId) {
        this.requestId = requestId;
    }

    public RpcResponse(long requestId, Integer status, String message, Integer encode, byte[] data) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.encode = encode;
        this.data = data;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getEncode() {
        return encode;
    }

    public void setEncode(Integer encode) {
        this.encode = encode;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "requestId=" + requestId +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", encode=" + encode +
                ", dataLength=" + (data == null? "null":data.length) +
                '}';
    }
}
