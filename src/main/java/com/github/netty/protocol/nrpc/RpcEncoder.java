package com.github.netty.protocol.nrpc;

import com.github.netty.core.AbstractProtocolEncoder;
import io.netty.channel.ChannelHandler;

/**
 * RPC encoder
 *
 *
 *-+------2B-------+--1B--+----1B----+------protocolHeaderLength----+-----dynamic-----+-------dynamic------------+
 * | packet length | type | ACK flag |           header             |      Fields     |          Body            |
 * |      55       |  1   |   1      |         MyProtocol           | 5mykey7myvalue  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------------------------+-----------------+--------------------------+
 *
 *
 * @author wangzihao
 */
@ChannelHandler.Sharable
public class RpcEncoder extends AbstractProtocolEncoder {

    public RpcEncoder() {
        setVersionBytes(RpcVersion.CURRENT_VERSION.getTextBytes());
    }

}
