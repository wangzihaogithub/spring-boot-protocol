package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;

import java.util.StringJoiner;

/**
 * 2019/3/17/017.
 *
 * @author wangzihao
 */
public class RpcPacket implements Recyclable {
    public static final byte TYPE_REQUEST = 1;
    public static final byte TYPE_RESPONSE = 2;
    public static final byte TYPE_PING = 3;
    public static final byte TYPE_PONG = 4;

    public static final byte ACK_NO = 0;
    public static final byte ACK_YES = 1;

    private final int packetType;
    /**
     * 1 = ack
     * 0 = no ack
     */
    private byte ack = ACK_NO;
    private byte[] data;
    private long packetLength;

    public RpcPacket(int packetType){
        this.packetType = packetType;
    }

    public long getPacketLength() {
        return packetLength;
    }

    public void setPacketLength(long packetLength) {
        this.packetLength = packetLength;
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

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",","{","}")
                .add("\"class\":\""+getClass().getSimpleName()+"\"")
                .add("\"ack\":"+ack)
                .add("\"packetType\":"+packetType);
//                .add("\"data\":"+ (data ==null?"null":"\""+new String(data).replace("\"", "\\\\\"") +"\""));
        toStringAppend(joiner);
        return joiner.toString();
    }

    protected void toStringAppend(StringJoiner joiner) {}

    @Override
    public void recycle() {

    }

    /**
     * Rpc Request
     */
    public static class RequestPacket extends RpcPacket {
        private static final Recycler<RequestPacket> RECYCLER = new Recycler<>(RequestPacket::new);
        private int requestId;
        private String requestMappingName;
        private String version;
        private String methodName;
        private int timeout;

        public static RequestPacket newInstance() {
            return RECYCLER.getInstance();
        }

        private RequestPacket() {
            super(TYPE_REQUEST);
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getRequestId() {
            return requestId;
        }

        public void setRequestId(int requestId) {
            this.requestId = requestId;
        }

        public String getRequestMappingName() {
            return requestMappingName;
        }

        public void setRequestMappingName(String requestMappingName) {
            this.requestMappingName = requestMappingName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        @Override
        public void recycle() {
//            RECYCLER.recycleInstance(this);
        }

        @Override
        public void toStringAppend(StringJoiner joiner) {
            joiner.add("\"requestId\":"+requestId);
            joiner.add("\"requestMappingName\":\""+requestMappingName+"\"");
            joiner.add("\"version\":\""+version+"\"");
            joiner.add("\"methodName\":\""+methodName+"\"");
            joiner.add("\"dataLength\":"+(getData() == null ? "null" : getData().length));
        }
    }

    /**
     * Rpc Response
     */
    public static class ResponsePacket extends RpcPacket{
        private static final Recycler<ResponsePacket> RECYCLER = new Recycler<>(ResponsePacket::new);
        private int requestId;
        private int status;
        private String message;
        private DataCodec.Encode encode;
        //正常返回
        public static final int OK = 200;
        //找不到方法
        public static final int NO_SUCH_METHOD = 404;
        //找不到服务
        public static final int NO_SUCH_SERVICE = 406;
        //服务器错误
        public static final int SERVER_ERROR = 500;

        private ResponsePacket() {
            super(TYPE_RESPONSE);
        }

        public static ResponsePacket newInstance() {
            return RECYCLER.getInstance();
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
        public void recycle() {
//            this.message = null;
//            this.encode = null;
//            this.setData(null);
//            RECYCLER.recycleInstance(this);
        }
        @Override
        public void toStringAppend(StringJoiner joiner) {
            joiner.add("\"requestId\":"+requestId);
            joiner.add("\"status\":"+status);
            if(message == null){
                joiner.add("\"message\":null");
            }else {
                joiner.add("\"message\":\"" + message.replace("\"", "\\\\\"") + "\"");
            }
            joiner.add("\"encode\":\""+encode+"\"");
            joiner.add("\"dataLength\":"+(getData() == null ? "null" : getData().length));
        }
    }

}