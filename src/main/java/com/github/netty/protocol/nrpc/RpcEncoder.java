package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractProtocolEncoder;
import io.netty.channel.ChannelHandler;

/**
 *  RPC encoder
 *
 *   ACK flag : (0=Don't need, 1=Need)
 *
 *-+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
 * |      76       |  1   |   1      |   NRPC/201 |     2       | 11serviceName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 */
public class RpcEncoder extends AbstractProtocolEncoder {
    private RpcVersion version = RpcVersion.CURRENT_VERSION;

    public RpcEncoder() {
        setVersionBytes(version.getTextBytes());
    }

    public RpcVersion getVersion() {
        return version;
    }
}
