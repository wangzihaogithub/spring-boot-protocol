package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.nrpc.codec.DataCodec;

import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * packet process
 *
 * <pre>
 * ---------------------------------------------------------------|
 * | client                       |           server              |
 * |--------------------------------------------------------------|
 * | TYPE_CLIENT_REQUEST -》      |                               |
 * |                              |      《- TYPE_RESPONSE_CHUNK  |
 * | TYPE_RESPONSE_CHUNK_ACK  -》 |                               |
 * |                              |      《- TYPE_RESPONSE_LAST   |
 * ----------------------------------------------------------------
 * </pre>
 * 2019/3/17/017.
 *
 * @author wangzihao
 */
public class RpcPacket implements Recyclable {
    /**
     * rpc request args
     */
    public static final byte TYPE_CLIENT_REQUEST = 1;
    /**
     * @see RpcClientChunkCompletableFuture#whenChunk(Consumer)
     */
    public static final byte TYPE_RESPONSE_CHUNK = 5;
    /**
     * @see RpcClientChunkCompletableFuture#whenChunkAck(RpcClientChunkCompletableFuture.Consumer3)
     */
    public static final byte TYPE_RESPONSE_CHUNK_ACK = 6;
    /**
     * rpc response data
     */
    public static final byte TYPE_RESPONSE_LAST = 2;

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

    public RpcPacket(int packetType) {
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
        StringJoiner joiner = new StringJoiner(",", "{", "}")
                .add("\"class\":\"" + getClass().getSimpleName() + "\"")
                .add("\"ack\":" + ack)
                .add("\"packetType\":" + packetType);
//                .add("\"data\":"+ (data ==null?"null":"\""+new String(data).replace("\"", "\\\\\"") +"\""));
        toStringAppend(joiner);
        return joiner.toString();
    }

    protected void toStringAppend(StringJoiner joiner) {
    }

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

        private RequestPacket() {
            super(TYPE_CLIENT_REQUEST);
        }

        public static RequestPacket newInstance() {
            return RECYCLER.getInstance();
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
            joiner.add("\"requestId\":" + requestId);
            joiner.add("\"requestMappingName\":\"" + requestMappingName + "\"");
            joiner.add("\"version\":\"" + version + "\"");
            joiner.add("\"methodName\":\"" + methodName + "\"");
            joiner.add("\"dataLength\":" + (getData() == null ? "null" : getData().length));
        }
    }

    /**
     * Rpc Response
     */
    public static class ResponsePacket extends RpcPacket {
        //正常返回
        public static final int OK = 200;
        //无后续正文
        public static final int NO_CONTENT = 204;
        //找不到方法
        public static final int NO_SUCH_METHOD = 404;
        //找不到服务
        public static final int NO_SUCH_SERVICE = 406;
        //服务器错误
        public static final int SERVER_ERROR = 500;
        private int requestId;
        private int status;
        private String message;
        private DataCodec.Encode encode;

        private ResponsePacket() {
            super(TYPE_RESPONSE_LAST);
        }

        protected ResponsePacket(int rpcType) {
            super(rpcType);
        }

        public static ResponsePacket newInstance(int rpcType) {
            switch (rpcType) {
                case TYPE_RESPONSE_CHUNK: {
                    return new ResponseChunkPacket();
                }
                case TYPE_RESPONSE_LAST: {
                    return new ResponseLastPacket();
                }
                case TYPE_RESPONSE_CHUNK_ACK: {
                    return new ResponseChunkAckPacket();
                }
                default: {
                    return new ResponsePacket();
                }
            }
        }

        public static ResponseLastPacket newLastPacket() {
            return new ResponseLastPacket();
        }

        public static ResponseChunkPacket newChunkPacket(int requestId, int chunkId) {
            ResponseChunkPacket chunk = new ResponseChunkPacket();
            chunk.setChunkId(chunkId);
            chunk.setRequestId(requestId);
            chunk.setMessage("");
            chunk.setAck(ACK_YES);
            chunk.setStatus(OK);
            return chunk;
        }

        public static ResponseChunkAckPacket newChunkAckPacket(int requestId, int ackChunkId) {
            ResponseChunkAckPacket chunk = new ResponseChunkAckPacket();
            chunk.setAckChunkId(ackChunkId);
            chunk.setRequestId(requestId);
            chunk.setMessage("");
            chunk.setAck(ACK_NO);
            chunk.setStatus(OK);
            return chunk;
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
            joiner.add("\"requestId\":" + requestId);
            joiner.add("\"status\":" + status);
            if (message == null) {
                joiner.add("\"message\":null");
            } else {
                joiner.add("\"message\":\"" + message.replace("\"", "\\\\\"") + "\"");
            }
            joiner.add("\"encode\":\"" + encode + "\"");
            joiner.add("\"dataLength\":" + (getData() == null ? "null" : getData().length));
        }
    }

    /**
     * Rpc chunk Response
     */
    public static class ResponseChunkPacket extends ResponsePacket {
        private int chunkId;

        public ResponseChunkPacket() {
            super(TYPE_RESPONSE_CHUNK);
        }

        public int getChunkId() {
            return chunkId;
        }

        public void setChunkId(int chunkId) {
            this.chunkId = chunkId;
        }

        @Override
        public void toStringAppend(StringJoiner joiner) {
            super.toStringAppend(joiner);
            joiner.add("\"chunkId\":" + chunkId);
        }
    }

    /**
     * Rpc chunk ack Response
     */
    public static class ResponseChunkAckPacket extends ResponsePacket {
        private int ackChunkId;

        public ResponseChunkAckPacket() {
            super(TYPE_RESPONSE_CHUNK_ACK);
        }

        public int getAckChunkId() {
            return ackChunkId;
        }

        public void setAckChunkId(int ackChunkId) {
            this.ackChunkId = ackChunkId;
        }

        @Override
        public void toStringAppend(StringJoiner joiner) {
            super.toStringAppend(joiner);
            joiner.add("\"ackChunkId\":" + ackChunkId);
        }
    }

    /**
     * Rpc last Response
     */
    public static class ResponseLastPacket extends ResponsePacket {
        public ResponseLastPacket() {
            super(TYPE_RESPONSE_LAST);
        }
    }
}