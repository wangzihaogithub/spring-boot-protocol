package com.github.netty.protocol.nrpc;


import com.github.netty.core.Packet;
import io.netty.util.AsciiString;

/**
 * Rpc Response
 */
public class RpcResponsePacket extends Packet {
    private static final AsciiString REQUEST_ID_KEY = AsciiString.of("requestId");;
    private static final AsciiString MESSAGE_KEY = AsciiString.of("message");;
    private static final AsciiString STATUS_KEY = AsciiString.of("status");
    private static final AsciiString ENCODE_KEY = AsciiString.of("encode");;

    public RpcResponsePacket() {
        super(TYPE_RESPONSE);
    }

    public AsciiString getRequestId() {
        return getFieldMap().get(REQUEST_ID_KEY);
    }

    public void setRequestId(AsciiString requestId) {
        getFieldMap().put(REQUEST_ID_KEY,requestId);
    }

    public AsciiString getMessage() {
        return getFieldMap().get(MESSAGE_KEY);
    }

    public void setMessage(AsciiString message) {
        getFieldMap().put(MESSAGE_KEY,message);
    }

    public RpcResponseStatus getStatus() {
        return RpcResponseStatus.indexOf(getFieldMap().get(STATUS_KEY));
    }

    public void setStatus(RpcResponseStatus status) {
        getFieldMap().put(STATUS_KEY,status.getCodeAscii());
    }

    public DataCodec.Encode getEncode() {
        return DataCodec.Encode.indexOf(getFieldMap().get(ENCODE_KEY));
    }

    public void setEncode(DataCodec.Encode encode) {
        getFieldMap().put(ENCODE_KEY,encode.getAscii());
    }

    @Override
    public String toString() {
        return "ResponsePacket{" +
                "requestId=" + getRequestId() +
                ", status=" + getStatus() +
                ", message='" + getMessage() + '\'' +
                ", encode=" + getEncode() +
                ", bodyLength=" + (getBody() == null ? "null" : getBody().length) +
                '}';
    }
}