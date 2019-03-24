package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;

import static com.github.netty.protocol.nrpc.DataCodec.CHARSET_UTF8;

/**
 * @author wangzihao
 */
public enum RpcResponseStatus {
    /**
     * OK
     */
    OK(200,"ok"),
    /**
     * NO_SUCH_METHOD
     */
    NO_SUCH_METHOD(400,"no such method"),
    /**
     * NO_SUCH_SERVICE
     */
    NO_SUCH_SERVICE(401,"no such service"),
    /**
     * SERVER_ERROR
     */
    SERVER_ERROR(500,"server error")
    ;

    private int code;
    private byte[] codeBytes;
    private byte[] textBytes;
    private ByteBuf codeByteBuf;

    RpcResponseStatus(int code,String text) {
        this.code = code;
        this.codeBytes = new byte[]{(byte) (code >>> 8), (byte) code};
        this.codeByteBuf = RecyclableUtil.newReadOnlyBuffer(codeBytes);
        this.textBytes = text.getBytes(CHARSET_UTF8);
    }

    public int getCode() {
        return code;
    }

    public ByteBuf getCodeByteBuf() {
        return RecyclableUtil.newReadOnlyBuffer(codeBytes);
    }

    public ByteBuf getTextByteBuf() {
        return RecyclableUtil.newReadOnlyBuffer(textBytes);
    }

    public static RpcResponseStatus indexOf(ByteBuf codeByteBuf){
        for(RpcResponseStatus value : values()){
            if(value.codeByteBuf.equals(codeByteBuf)){
                return value;
            }
        }
        throw new IllegalArgumentException("value=" + codeByteBuf.toString(CHARSET_UTF8));
    }

}
