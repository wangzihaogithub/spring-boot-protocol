package com.github.netty.protocol.nrpc;

import io.netty.util.AsciiString;

public enum RpcResponseStatus {
    OK(200,"ok"),
    NO_SUCH_METHOD(400,"no such method"),
    NO_SUCH_SERVICE(401,"no such service"),
    SERVER_ERROR(500,"server error")
    ;

    private int code;
    private AsciiString codeAscii;
    private AsciiString textAscii;

    RpcResponseStatus(int code,CharSequence text) {
        this.code = code;
        this.codeAscii = new AsciiString(new byte[]{(byte) (code >>> 8), (byte) code},false);
        this.textAscii = AsciiString.of(text);
    }

    public int getCode() {
        return code;
    }

    public AsciiString getCodeAscii() {
        return codeAscii;
    }

    public AsciiString getTextAscii() {
        return textAscii;
    }

    public static RpcResponseStatus indexOf(AsciiString codeAscii){
        for(RpcResponseStatus value : values()){
            if(value.codeAscii.equals(codeAscii)){
                return value;
            }
        }
        return null;
    }

}
