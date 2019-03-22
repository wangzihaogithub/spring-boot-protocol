package com.github.netty.protocol.nrpc;


import com.github.netty.core.Packet;
import io.netty.util.AsciiString;

/**
 * Rpc Request
 */
public class RpcRequestPacket extends Packet {
    private static final AsciiString REQUEST_ID_KEY = AsciiString.of("requestId");;
    private static final AsciiString SERVICE_NAME_KEY = AsciiString.of("serviceName");
    private static final AsciiString METHOD_NAME_KEY = AsciiString.of("methodName");;

    public RpcRequestPacket() {
        super(TYPE_REQUEST);
    }

    public AsciiString getRequestId() {
        return getFieldMap().get(REQUEST_ID_KEY);
    }

    public void setRequestId(AsciiString requestId) {
        getFieldMap().put(REQUEST_ID_KEY,requestId);
    }

    public AsciiString getServiceName() {
        return getFieldMap().get(SERVICE_NAME_KEY);
    }

    public void setServiceName(AsciiString serviceName) {
        getFieldMap().put(SERVICE_NAME_KEY,serviceName);
    }

    public AsciiString getMethodName() {
        return getFieldMap().get(METHOD_NAME_KEY);
    }

    public void setMethodName(AsciiString methodName) {
        getFieldMap().put(METHOD_NAME_KEY,methodName);
    }

    @Override
    public String toString() {
        return "RequestPacket{" +
                "requestId=" + getRequestId() +
                ", serviceName='" + getServiceName() + '\'' +
                ", methodName='" + getMethodName() + '\'' +
                ", bodyLength=" + (getBody() == null ? "null" : getBody().length) +
                '}';
    }
}
