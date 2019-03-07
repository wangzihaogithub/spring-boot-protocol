package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;

import java.util.function.Supplier;

import static com.github.netty.core.util.IOUtil.*;
import static com.github.netty.protocol.nrpc.RpcEncoder.PROTOCOL_HEADER;
import static com.github.netty.protocol.nrpc.RpcEncoder.RPC_CHARSET;

/**
 *  RPC decoder
 *
 *   Request Packet (note:  1 = request type)
 *-+------8B--------+--1B--+------2B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----2B------+-------length-------------+
 * | header/version | type | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 *
 *
 *   Response Packet (note: 2 = response type)
 *-+------8B--------+--1B--+------2B------+-----4B-----+---1B---+--------1B------+--length--+---1B---+-----2B------+----------length----------+
 * | header/version | type | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 * @author wangzihao
 */
public class RpcDecoder extends LengthFieldBasedFrameDecoder {
    /**
     * Packet minimum length
     * ProtocolHeader(8B)  + Type(1B) + TotalLength(2B) + DataLength(2B)
     */
    public static final int MIN_PACKET_LENGTH =  PROTOCOL_HEADER.length + BYTE_LENGTH + CHAR_LENGTH * 2 ;
    private static final byte[] EMPTY = new byte[0];

    private Supplier<RpcRequest> requestSupplier;
    private Supplier<RpcResponse> responseSupplier;

    public RpcDecoder(Supplier<RpcRequest> requestSupplier,Supplier<RpcResponse> responseSupplier) {
        this(10 * 1024 * 1024, requestSupplier,responseSupplier);
    }

    public RpcDecoder(int maxLength,Supplier<RpcRequest> requestSupplier,Supplier<RpcResponse> responseSupplier) {
        super(maxLength, PROTOCOL_HEADER.length + BYTE_LENGTH,CHAR_LENGTH,0,0,true);
        this.requestSupplier = requestSupplier;
        this.responseSupplier = responseSupplier;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf msg = (ByteBuf) super.decode(ctx, buffer);
        if(msg == null || msg.readableBytes() < MIN_PACKET_LENGTH){
            return null;
        }

        try {
            return decodeToPojo(msg);
        }finally {
            if(msg.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
    }

    /**
     * Resolve to the entity class
     * @param msg msg
     * @return
     */
    protected Object decodeToPojo(ByteBuf msg){
        //Skip protocol header
        msg.skipBytes(PROTOCOL_HEADER.length);

        byte rpcType = msg.readByte();
        switch (rpcType){
            case RpcRequest.RPC_TYPE:{
                RpcRequest request = requestSupplier.get();
                //skip total length
                msg.skipBytes(CHAR_LENGTH);

                //Request ID
                request.setRequestId(msg.readInt());

                //Request service
                request.setServiceName(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request method
                request.setMethodName(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request data
                int dataLength = msg.readUnsignedShort();
                if(dataLength > 0) {
                    request.setData(new byte[dataLength]);
                    msg.readBytes(request.getData());
                }else {
                    request.setData(EMPTY);
                }
                return request;
            }
            case RpcResponse.RPC_TYPE:{
                RpcResponse response = responseSupplier.get();
                //skip total length
                msg.skipBytes(CHAR_LENGTH);

                //Request ID
                response.setRequestId(msg.readInt());

                //Response status
                response.setStatus((int) msg.readUnsignedByte());

                //Response encode
                response.setEncode(DataCodec.Encode.indexOf(msg.readUnsignedByte()));

                //Response information
                response.setMessage(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request data
                int dataLength = msg.readUnsignedShort();
                if(dataLength > 0) {
                    response.setData(new byte[dataLength]);
                    msg.readBytes(response.getData());
                }else {
                    response.setData(null);
                }
                return response;
            }
            default:{
                //Unknown type
                msg.readerIndex(msg.readableBytes());
                return null;
            }
        }
    }

}
