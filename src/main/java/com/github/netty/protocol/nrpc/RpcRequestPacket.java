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
 * Rpc Request
 * @author wangzihao
 */
public class RpcRequestPacket extends Packet {
    private static final byte[] REQUEST_ID_BYTES = "requestId".getBytes(CHARSET_UTF8);
    private static final byte[] SERVICE_NAME_BYTES = "serviceName".getBytes(CHARSET_UTF8);
    private static final byte[] METHOD_NAME_BYTES = "methodName".getBytes(CHARSET_UTF8);

//    private static final ByteBuf REQUEST_ID_KEY = RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES);
//    private static final ByteBuf SERVICE_NAME_KEY = RecyclableUtil.newReadOnlyBuffer(SERVICE_NAME_BYTES);
//    private static final ByteBuf METHOD_NAME_KEY = RecyclableUtil.newReadOnlyBuffer(METHOD_NAME_BYTES);

    private static final AsciiString REQUEST_ID_KEY = AsciiString.of("requestId");
    private static final AsciiString SERVICE_NAME_KEY = AsciiString.of("serviceName");
    private static final AsciiString METHOD_NAME_KEY = AsciiString.of("methodName");
    private static final FastThreadLocal<Map<AsciiString,ByteBuf>> FIELD_MAP_THREAD_LOCAL = new FastThreadLocal<Map<AsciiString,ByteBuf>>(){
        @Override
        protected Map<AsciiString, ByteBuf> initialValue() throws Exception {
            return new ConcurrentHashMap<>(32);
//            return new FixedArrayMap<>(Byte.MAX_VALUE * 2);
        }
    };

    public RpcRequestPacket() {
        super(TYPE_REQUEST);
        setFieldMap(FIELD_MAP_THREAD_LOCAL.get());
//        setFieldMap(new FixedArrayMap<>(3));
//        setFieldMap(new ConcurrentHashMap<>(3));
    }

    public void setRequestId(ByteBuf requestId) {
//        putField(RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES),requestId);
        putField(REQUEST_ID_KEY,requestId);
    }

    public void setServiceName(ByteBuf serviceName) {
//        putField(RecyclableUtil.newReadOnlyBuffer(SERVICE_NAME_BYTES),serviceName);
        putField(SERVICE_NAME_KEY,serviceName);
    }

    public void setMethodName(ByteBuf methodName) {
//        putField(RecyclableUtil.newReadOnlyBuffer(METHOD_NAME_BYTES),methodName);
        putField(METHOD_NAME_KEY,methodName);
    }

    public ByteBuf getServiceName() {
        return getFieldMap().get(SERVICE_NAME_KEY);
    }

    public ByteBuf getRequestId() {
        return getFieldMap().get(REQUEST_ID_KEY);
    }

    public ByteBuf getMethodName() {
        return getFieldMap().get(METHOD_NAME_KEY);
    }

    public String getServiceNameString() {
        return getServiceName().toString(CHARSET_UTF8);
    }

    public String getMethodNameString() {
        return getMethodName().toString(CHARSET_UTF8);
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
                throw new IllegalStateException("error requestId. byteBuf="+byteBuf);
            }
        }
    }

}
