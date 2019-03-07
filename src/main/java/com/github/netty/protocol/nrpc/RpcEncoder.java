package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.github.netty.core.util.IOUtil.BYTE_LENGTH;
import static com.github.netty.core.util.IOUtil.CHAR_LENGTH;
import static com.github.netty.core.util.IOUtil.INT_LENGTH;


/**
 * RPC encoder
 *
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
@ChannelHandler.Sharable
public class RpcEncoder extends MessageToByteEncoder {
    /**
     * protocol header
     * Fixed 8 length
     */
    public static final byte[] PROTOCOL_HEADER = new byte[]{'N','R','P','C','/',0,1,0};
    public static final Charset RPC_CHARSET = StandardCharsets.UTF_8;
    /**
     * Fixed request length (note : Not including the total length.)
     * 4B + 1B + 1B + 2B
     */
    private static final int FIXED_REQUEST_LENGTH = INT_LENGTH + BYTE_LENGTH + BYTE_LENGTH + CHAR_LENGTH;
    /**
     * Fixed response length (note : Not including the total length.)
     * 4B + 1B + 1B + 1B + 2B
     */
    private static final int FIXED_RESPONSE_LENGTH = INT_LENGTH + BYTE_LENGTH + BYTE_LENGTH + BYTE_LENGTH + CHAR_LENGTH;

    public RpcEncoder() {}

    @Override
    public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {
        if(in instanceof RpcRequest){
            RpcRequest request = (RpcRequest) in;
            int writeCurrentLength;
            int writeTotalLength = FIXED_REQUEST_LENGTH;

            //(8 byte) protocol head
            out.writeBytes(PROTOCOL_HEADER);

            //(1 byte Unsigned) RPC type (1 = request , 2 = response)
            out.writeByte(RpcRequest.RPC_TYPE);

            //(2 byte Unsigned) total length
            int writerTotalLengthIndex = out.writerIndex();
            out.writerIndex(writerTotalLengthIndex + CHAR_LENGTH);

            //(4 byte) Request ID
            out.writeInt(request.getRequestId());

            //(length byte) service name
            out.writerIndex(out.writerIndex() + BYTE_LENGTH);
            writeCurrentLength = out.writeCharSequence(request.getServiceName(), RPC_CHARSET);

            //(1 byte Unsigned) service length
            out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH,writeCurrentLength);
            writeTotalLength += writeCurrentLength;

            //(length byte Unsigned)  method name
            out.writerIndex(out.writerIndex() + BYTE_LENGTH);
            writeCurrentLength = out.writeCharSequence(request.getMethodName(), RPC_CHARSET);

            //(1 byte Unsigned) method length
            out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH,writeCurrentLength);
            writeTotalLength += writeCurrentLength;

            //(2 byte Unsigned) data length
            byte[] data = request.getData();
            out.writeChar(data.length);
            if(data.length > 0){
                //(length byte)  data
                out.writeBytes(data);
                writeTotalLength += data.length;
            }

            //set total length Unsigned
            out.setChar(writerTotalLengthIndex,writeTotalLength);

        }else if(in instanceof RpcResponse){
            RpcResponse response = (RpcResponse) in;
            int writeCurrentLength;
            int writeTotalLength = FIXED_RESPONSE_LENGTH;

            //(8 byte) protocol head
            out.writeBytes(PROTOCOL_HEADER);

            //(1 byte Unsigned) RPC type (1 = request , 2 = response)
            out.writeByte(RpcResponse.RPC_TYPE);

            //(2 byte Unsigned) total length
            int writerTotalLengthIndex = out.writerIndex();
            out.writerIndex(writerTotalLengthIndex + CHAR_LENGTH);

            //(4 byte) Request ID
            out.writeInt(response.getRequestId());

            //(1 byte Unsigned) Response status
            out.writeByte(response.getStatus());

            //(1 byte Unsigned) Whether the data has been encoded
            out.writeByte(response.getEncode().getIndex());

            //(length byte) Response information
            out.writerIndex(out.writerIndex() + BYTE_LENGTH);
            writeCurrentLength = out.writeCharSequence(response.getMessage(), RPC_CHARSET);

            //(1 byte Unsigned) Response information length
            out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH,writeCurrentLength);
            writeTotalLength += writeCurrentLength;

            //(2 byte Unsigned) data length
            byte[] data = response.getData();
            writeCurrentLength = data == null? 0: data.length;
            out.writeChar(writeCurrentLength);
            if(writeCurrentLength > 0) {
                out.writeBytes(data);
                //(length byte)  data
                writeTotalLength += writeCurrentLength;
            }

            //set total length
            out.setChar(writerTotalLengthIndex,writeTotalLength);
        }
    }

}
