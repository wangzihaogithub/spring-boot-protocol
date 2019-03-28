package com.github.netty.protocol.nrpc;

import com.github.netty.core.Packet;
import com.github.netty.core.util.FixedArrayMap;
import com.github.netty.core.util.IOUtil;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.netty.protocol.nrpc.DataCodec.CHARSET_UTF8;

/**
 * Rpc Response
 * @author wangzihao
 */
public class RpcResponsePacket extends Packet {
    private static final byte[] REQUEST_ID_BYTES = "requestId".getBytes(CHARSET_UTF8);
    private static final byte[] MESSAGE_BYTES = "message".getBytes(CHARSET_UTF8);
    private static final byte[] STATUS_BYTES = "status".getBytes(CHARSET_UTF8);
    private static final byte[] ENCODE_BYTES = "encode".getBytes(CHARSET_UTF8);

//    private static final ByteBuf REQUEST_ID_KEY = RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES);
//    private static final ByteBuf MESSAGE_KEY = RecyclableUtil.newReadOnlyBuffer(MESSAGE_BYTES);
//    private static final ByteBuf STATUS_KEY = RecyclableUtil.newReadOnlyBuffer(STATUS_BYTES);
//    private static final ByteBuf ENCODE_KEY = RecyclableUtil.newReadOnlyBuffer(ENCODE_BYTES);

    private static final AsciiString REQUEST_ID_KEY = AsciiString.of("requestId");
    private static final AsciiString MESSAGE_KEY = AsciiString.of("message");
    private static final AsciiString STATUS_KEY = AsciiString.of("status");
    private static final AsciiString ENCODE_KEY = AsciiString.of("encode");

    private static final FastThreadLocal<Map<AsciiString,ByteBuf>> FIELD_MAP_THREAD_LOCAL = new FastThreadLocal<Map<AsciiString,ByteBuf>>(){
        @Override
        protected Map<AsciiString, ByteBuf> initialValue() throws Exception {
            return new ConcurrentHashMap<>(32);
//            return new FixedArrayMap<>(Byte.MAX_VALUE * 2);
        }
    };

    public RpcResponsePacket() {
        super(TYPE_RESPONSE);
        setFieldMap(FIELD_MAP_THREAD_LOCAL.get());
//        setFieldMap(new FixedArrayMap<>(4));
//        setFieldMap(new ConcurrentHashMap<>(4));
    }

    public ByteBuf getRequestId() {
        return getFieldMap().get(REQUEST_ID_KEY);
    }

    public int getRequestIdInt() {
        ByteBuf byteBuf = getRequestId();
        int readableBytes = byteBuf.readableBytes();
        switch (readableBytes){
            case IOUtil.BYTE_LENGTH :{
                return byteBuf.getByte(0);
            }
            case IOUtil.CHAR_LENGTH :{
                return byteBuf.getChar(0);
            }
            case IOUtil.INT_LENGTH :{
                return byteBuf.getInt(0);
            }default:{
                throw new IllegalStateException("error requestId");
            }
        }
    }

    public void setRequestId(ByteBuf requestId) {
//        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES),requestId);
        putField(REQUEST_ID_KEY,requestId);
    }

    public void setStatus(RpcResponseStatus status) {
//        putField(RecyclableUtil.newReadOnlyBuffer(STATUS_BYTES),status.getCodeByteBuf());
        putField(STATUS_KEY,status.getCodeByteBuf());
    }

    public void setEncode(DataCodec.Encode encode) {
//        putField(RecyclableUtil.newReadOnlyBuffer(ENCODE_BYTES),encode.getByteBuf());
        putField(ENCODE_KEY,encode.getByteBuf());
    }

    public void setMessage(ByteBuf message) {
//        putField(RecyclableUtil.newReadOnlyBuffer(MESSAGE_BYTES),message);
        putField(MESSAGE_KEY,message);
    }

    public RpcResponseStatus getStatus() {
        return RpcResponseStatus.indexOf(getFieldMap().get(STATUS_KEY));
    }

    public DataCodec.Encode getEncode() {
        return DataCodec.Encode.indexOf(getFieldMap().get(ENCODE_KEY));
    }

    public ByteBuf getMessage() {
        return getFieldMap().get(MESSAGE_KEY);
    }

    public String getMessageString() {
        return getMessage().toString(CHARSET_UTF8);
    }

}