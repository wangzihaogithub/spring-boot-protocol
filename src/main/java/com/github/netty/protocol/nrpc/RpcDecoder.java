package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractProtocolDecoder;
import com.github.netty.core.Packet;

/**
 *  RPC decoder
 *
 *   Request Packet
 *
 *   ACK flag : (0=Don't need, 1=Need)
 *
 *-+------2B-------+--1B--+----1B----+-----8B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  |                Fields                      |          Body            |
 * |      75       |  1   |   1      |   NRPC/201 | 11serviceName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 */
public class RpcDecoder extends AbstractProtocolDecoder {

    public RpcDecoder() {
        super(RpcVersion.CURRENT_VERSION.getTextBytes().length);
    }

    public RpcDecoder(int maxLength) {
        super(RpcVersion.CURRENT_VERSION.getTextBytes().length, maxLength);
    }

    /**
     * new packet
     * @param packetType
     * @return
     */
    @Override
    protected Packet newPacket(int packetType) {
        switch (packetType){
            case Packet.TYPE_REQUEST:{
                return new RpcRequestPacket();
            }
            case Packet.TYPE_RESPONSE:{
                return new RpcResponsePacket();
            }
            default:{
                return super.newPacket(packetType);
            }
        }
    }

}
