package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.IOUtil;

import java.nio.charset.StandardCharsets;

/**
 * RPC请求
 * @author 84215
 */
public class RpcRequest {

    private boolean serviceNameDecodeFlag;
    private boolean methodNameDecodeFlag;

    private byte[] serviceNameBytes;
    private byte[] methodNameBytes;

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

    byte[] getServiceNameBytes() {
        return serviceNameBytes;
    }

    void setServiceNameBytes(byte[] serviceNameBytes) {
        this.serviceNameBytes = serviceNameBytes;
    }

    byte[] getMethodNameBytes() {
        return methodNameBytes;
    }

    void setMethodNameBytes(byte[] methodNameBytes) {
        this.methodNameBytes = methodNameBytes;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getServiceName() {
        if(!serviceNameDecodeFlag && serviceNameBytes != null){
            serviceNameDecodeFlag = true;
            serviceName = IOUtil.getString(serviceNameBytes,StandardCharsets.UTF_8);
            serviceNameBytes = null;
        }
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMethodName() {
        if(!methodNameDecodeFlag && methodNameBytes != null){
            methodNameDecodeFlag = true;
            methodName = IOUtil.getString(methodNameBytes, StandardCharsets.UTF_8);
            methodNameBytes = null;
        }
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
