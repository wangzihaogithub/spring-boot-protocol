package com.github.netty.protocol.nrpc;

/**
 * 2019/3/17/017.
 *
 * @author acer01
 */
public class RpcPacket {
    public static final byte REQUEST_TYPE = 1;
    public static final byte RESPONSE_TYPE = 2;
    public static final byte PING_TYPE = 3;
    public static final byte PONG_TYPE = 4;

    public static final byte ACK_NO = 0;
    public static final byte ACK_YES = 1;

    private final int packetType;
    /**
     * 1 = ack
     * 0 = no ack
     */
    private byte ack = ACK_NO;
    private byte[] data;

    public RpcPacket(int packetType) {
        this.packetType = packetType;
    }

    public int getAck() {
        return ack;
    }

    public void setAck(byte ack) {
        this.ack = ack;
    }

    public int getPacketType() {
        return packetType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Rpc Request
     */
    public static class RequestPacket extends RpcPacket {
        private int requestId;
        private String serviceName;
        private String methodName;

        public RequestPacket() {
            super(REQUEST_TYPE);
        }

        public RequestPacket(int requestId, String serviceName, String methodName, byte[] data) {
            this();
            this.requestId = requestId;
            this.serviceName = serviceName;
            this.methodName = methodName;
            setData(data);
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

        @Override
        public String toString() {
            return "RpcRequest{" +
                    "requestId=" + requestId +
                    ", serviceName='" + serviceName + '\'' +
                    ", methodName='" + methodName + '\'' +
                    ", dataLength=" + (getData() == null ? "null" : getData().length) +
                    '}';
        }
    }

    /**
     * Rpc Response
     */
    public static class ResponsePacket extends RpcPacket{
        private int requestId;
        private int status;
        private String message;
        private DataCodec.Encode encode;

        public ResponsePacket() {
            super(RESPONSE_TYPE);
        }

        public ResponsePacket(int requestId) {
            this();
            this.requestId = requestId;
        }

        public ResponsePacket(int requestId, Integer status, String message, DataCodec.Encode encode, byte[] data) {
            this();
            this.requestId = requestId;
            this.status = status;
            this.message = message;
            this.encode = encode;
            setData(data);
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


        @Override
        public String toString() {
            return "RpcResponse{" +
                    "requestId=" + requestId +
                    ", status=" + status +
                    ", message='" + message + '\'' +
                    ", encode=" + encode +
                    ", dataLength=" + (getData() == null ? "null" : getData().length) +
                    '}';
        }
    }

}