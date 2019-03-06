package com.github.netty.protocol.nrpc;

/**
 * Rpc Response
 * @author wangzihao
 */
public class RpcResponse {

    private int requestId;
    private int status;
    private String message;
    private DataCodec.Encode encode;
    private byte[] data;

    public static final int OK = 200;
    public static final int NO_SUCH_METHOD = 400;
    public static final int NO_SUCH_SERVICE = 401;
    public static final int SERVER_ERROR = 500;

    public static final byte RPC_TYPE = 2;

    public RpcResponse() {
    }

    public RpcResponse(int requestId) {
        this.requestId = requestId;
    }

    public RpcResponse(int requestId, Integer status, String message, DataCodec.Encode encode, byte[] data) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.encode = encode;
        this.data = data;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
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

    public DataCodec.Encode getEncode() {
        return encode;
    }

    public void setEncode(DataCodec.Encode encode) {
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
