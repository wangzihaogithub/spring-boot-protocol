package com.github.netty.protocol.nrpc;

/**
 * RPC请求
 * @author 84215
 */
public class RpcRequest {

    private int requestId;
    private String serviceName;
    private String methodName;
    private byte[] data;

    public RpcRequest() {
    }

    public RpcRequest(int requestId, String serviceName, String methodName, byte[] data) {
        this.requestId = requestId;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.data = data;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "RpcRequest{" +
                "requestId=" + requestId +
                ", serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", dataLength=" + (data == null? "null":data.length) +
                '}';
    }
}
