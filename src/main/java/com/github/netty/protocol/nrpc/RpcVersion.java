package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;

/**
 * version
 * @author wangzihao
 */
public enum RpcVersion {
    V2_0_0(new byte[]{'N','R','P','C','/',2,0,0}),
    V2_0_1(new byte[]{'N','R','P','C','/',2,0,1});

    private String text;
    private byte[] textBytes;

    public static final RpcVersion CURRENT_VERSION = RpcVersion.V2_0_1;

    RpcVersion(byte[] textBytes) {
        this.textBytes = textBytes;
        StringBuilder sb = new StringBuilder();
        for (byte textByte : textBytes) {
            sb.append(Integer.toString(textByte));
        }
        this.text = sb.toString();
    }

    public String getText() {
        return text;
    }

    public byte[] getTextBytes() {
        return textBytes;
    }

    /**
     * Whether the NRPC protocol to support
     * @param msg message
     * @return true=yes, false=no
     */
    public boolean isSupport(ByteBuf msg){
        // min packet is 12 length
        if(msg == null || msg.readableBytes() < 12){
            return false;
        }
        if(msg.getByte(4) == 'N'
                && msg.getByte(5) == 'R'
                && msg.getByte(6) == 'P'
                && msg.getByte(7) == 'C'
                ){
            return false;
        }
        return true;
    }
}
