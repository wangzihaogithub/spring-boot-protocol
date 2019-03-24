package com.github.netty.protocol.nrpc;

import com.github.netty.core.Packet;
import com.github.netty.core.util.FixedArrayMap;
import com.github.netty.core.util.IOUtil;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.Map;

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

    private static final ByteBuf REQUEST_ID_KEY = RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES);
    private static final ByteBuf MESSAGE_KEY = RecyclableUtil.newReadOnlyBuffer(MESSAGE_BYTES);
    private static final ByteBuf STATUS_KEY = RecyclableUtil.newReadOnlyBuffer(STATUS_BYTES);
    private static final ByteBuf ENCODE_KEY = RecyclableUtil.newReadOnlyBuffer(ENCODE_BYTES);

    private static final FastThreadLocal<Map<ByteBuf,ByteBuf>> FIELD_MAP_THREAD_LOCAL = new FastThreadLocal<Map<ByteBuf,ByteBuf>>(){
        @Override
        protected Map<ByteBuf, ByteBuf> initialValue() throws Exception {
            return new FixedArrayMap<>(Byte.MAX_VALUE * 2);
        }
    };

    public RpcResponsePacket() {
        super(TYPE_RESPONSE);
        setFieldMap(FIELD_MAP_THREAD_LOCAL.get());
//        setFieldMap(new FixedArrayMap<>(4));
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
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES),requestId);
    }

    public void setStatus(RpcResponseStatus status) {
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(STATUS_BYTES),status.getCodeByteBuf());
    }

    public void setEncode(DataCodec.Encode encode) {
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(ENCODE_BYTES),encode.getByteBuf());
    }

    public void setMessage(ByteBuf message) {
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(MESSAGE_BYTES),message);
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