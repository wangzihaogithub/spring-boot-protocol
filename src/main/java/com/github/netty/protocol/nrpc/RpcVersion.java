package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;

/**
 * nrpc protocol version
 * @author wangzihao
 */
public enum RpcVersion {
    /**
     * spring-boot-protocol 2.0.0
     */
    V2_0_0("NRPC/200", new byte[]{'N','R','P','C','/',2,0,0}),
    /**
     * spring-boot-protocol 2.0.1
     */
    V2_0_1("NRPC/201", new byte[]{'N','R','P','C','/',2,0,1});

    private String text;
    private byte[] textBytes;

    public static final RpcVersion CURRENT_VERSION = RpcVersion.V2_0_1;

    RpcVersion(String text, byte[] textBytes) {
        this.text = text;
        this.textBytes = textBytes;
    }

    public String getText() {
        return text;
    }

    public byte[] getTextBytes() {
        return textBytes;
    }



    private static final byte[] PROTOCOL_NAME = {'N','R','P','C'};
    private static final int PACKET_VERSION_AT_OFFSET = 4;
    /**
     * Whether the NRPC protocol to support
     *
     * |-----PACKET_VERSION_AT_OFFSET----|
     *
     *-+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
     * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
     * |      76       |  1   |   1      |   NRPC0201 |     2       | 11serviceName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
     *-+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
     * @param msg message
     * @return true=yes, false=no
     */
    public boolean isSupport(ByteBuf msg){
        // min packet is 12 length
        if(msg == null || msg.readableBytes() < 13){
            return false;
        }

        for(int i = 0; i< PROTOCOL_NAME.length; i++){
            if(PROTOCOL_NAME[i] != msg.getByte(i + PACKET_VERSION_AT_OFFSET)){
                return false;
            }
        }
        return true;
    }

}
