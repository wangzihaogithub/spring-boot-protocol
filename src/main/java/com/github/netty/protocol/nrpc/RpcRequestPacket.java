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
 * Rpc Request
 * @author wangzihao
 */
public class RpcRequestPacket extends Packet {
    private static final byte[] REQUEST_ID_BYTES = "requestId".getBytes(CHARSET_UTF8);
    private static final byte[] SERVICE_NAME_BYTES = "serviceName".getBytes(CHARSET_UTF8);
    private static final byte[] METHOD_NAME_BYTES = "methodName".getBytes(CHARSET_UTF8);

    private static final ByteBuf REQUEST_ID_KEY = RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES);
    private static final ByteBuf SERVICE_NAME_KEY = RecyclableUtil.newReadOnlyBuffer(SERVICE_NAME_BYTES);
    private static final ByteBuf METHOD_NAME_KEY = RecyclableUtil.newReadOnlyBuffer(METHOD_NAME_BYTES);
    private static final FastThreadLocal<Map<ByteBuf,ByteBuf>> FIELD_MAP_THREAD_LOCAL = new FastThreadLocal<Map<ByteBuf,ByteBuf>>(){
        @Override
        protected Map<ByteBuf, ByteBuf> initialValue() throws Exception {
            return new FixedArrayMap<>(Byte.MAX_VALUE * 2);
        }
    };

    public RpcRequestPacket() {
        super(TYPE_REQUEST);
        setFieldMap(FIELD_MAP_THREAD_LOCAL.get());
//        setFieldMap(new FixedArrayMap<>(3));
    }

    public void setRequestId(ByteBuf requestId) {
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(REQUEST_ID_BYTES),requestId);
    }

    public void setServiceName(ByteBuf serviceName) {
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(SERVICE_NAME_BYTES),serviceName);
    }

    public void setMethodName(ByteBuf methodName) {
        getFieldMap().put(RecyclableUtil.newReadOnlyBuffer(METHOD_NAME_BYTES),methodName);
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
