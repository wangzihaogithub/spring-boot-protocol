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

    /**
     * Whether the NRPC protocol to support
     *
     *   Request Packet (note:  1 = request type)
     *-+------8B--------+--1B--+--1B--+------2B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----2B------+-------length-------------+
     * | header/version | type | ACK   | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
     * |   NRPC/010     |  1   | 1    |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
     *-+----------------+------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
     *
     *
     *   Response Packet (note: 2 = response type)
     *-+------8B--------+--1B--+--1B--+------2B------+-----4B-----+---1B---+--------1B------+--length--+---1B---+-----2B------+----------length----------+
     * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
     * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
     *-+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
     *
     * @param msg message
     * @return true=yes, false=no
     */
    public boolean isSupport(ByteBuf msg){
        // min packet is 8 length
        if(msg == null || msg.readableBytes() < 8){
            return false;
        }

        for(int i = 0; i< PROTOCOL_NAME.length; i++){
            if(PROTOCOL_NAME[i] != msg.getByte(i)){
                return false;
            }
        }
        return true;
    }

}
