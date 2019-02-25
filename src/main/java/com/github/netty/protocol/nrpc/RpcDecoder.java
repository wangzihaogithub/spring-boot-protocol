package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;

import java.util.function.Supplier;

import static com.github.netty.core.util.IOUtil.INT_LENGTH;
import static com.github.netty.core.util.IOUtil.BYTE_LENGTH;
import static com.github.netty.protocol.nrpc.RpcEncoder.*;

/**
 *  RPC decoder
 * @author wangzihao
 */
public class RpcDecoder extends DelimiterBasedFrameDecoder {
    public static final byte[] EMPTY = new byte[0];
    /**
     * Packet minimum length
     */
    public static final int MIN_PACKET_LENGTH =
            //Protocol header length + protocol header length + minimum length of 4 fields + terminator
            BYTE_LENGTH + PROTOCOL_HEADER.length + INT_LENGTH * 4 + END_DELIMITER.length;
    private Supplier pojoSupplier;

    public RpcDecoder(Supplier pojoSupplier) {
        this(2 * 1024 * 1024, pojoSupplier);
    }

    public RpcDecoder(int maxLength,Supplier pojoSupplier) {
        super(maxLength, true, true, new ByteBuf[]{Unpooled.wrappedBuffer(END_DELIMITER)});
        this.pojoSupplier = pojoSupplier;
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
     * @param msg
     * @return
     */
    private Object decodeToPojo(ByteBuf msg){
        Object pojo = pojoSupplier.get();

        if(pojo instanceof RpcRequest){
            RpcRequest request = (RpcRequest) pojo;

            //Skip protocol header
            int protocolLength = msg.readByte();
            msg.skipBytes(protocolLength);

            //Request ID
            request.setRequestId(msg.readInt());

            //Request service
            request.setServiceName(msg.readCharSequence(msg.readInt(), RPC_CHARSET).toString());

            //Request method
            request.setMethodName(msg.readCharSequence(msg.readInt(), RPC_CHARSET).toString());

            //Request data
            int dataLength = msg.readInt();
            if(dataLength > 0) {
                request.setData(new byte[dataLength]);
                msg.readBytes(request.getData());
            }else {
                request.setData(EMPTY);
            }
            return pojo;
        }else if(pojo instanceof RpcResponse){
            RpcResponse response = (RpcResponse) pojo;

            //Skip protocol header
            int protocolLength = msg.readByte();
            msg.skipBytes(protocolLength);

            //Request ID
            response.setRequestId(msg.readInt());

            //Request service
            response.setStatus(msg.readInt());

            //Whether the data has been encoded
            response.setEncode(msg.readByte());

            //Response information
            response.setMessage(msg.readCharSequence(msg.readInt(), RPC_CHARSET).toString());

            //Request data
            int dataLength = msg.readInt();
            if(dataLength > 0) {
                response.setData(new byte[dataLength]);
                msg.readBytes(response.getData());
            }else {
                response.setData(EMPTY);
            }
            return pojo;
        }else {
            return null;
        }
    }

}
