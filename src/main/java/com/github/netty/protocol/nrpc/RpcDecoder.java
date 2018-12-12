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
 *  RPC 解码器
 * @author 84215
 */
public class RpcDecoder extends DelimiterBasedFrameDecoder {
    /**
     * 数据包最小长度
     */
    public static final int MIN_PACKET_LENGTH =
            //协议头长度 + 协议头 + 4个字段最小长度 + 结束符
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
     * 解析至实体类
     * @param msg
     * @return
     */
    private Object decodeToPojo(ByteBuf msg){
        Object pojo = pojoSupplier.get();

        if(pojo instanceof RpcRequest){
            RpcRequest request = (RpcRequest) pojo;

            //跳过协议头
            int protocolLength = msg.readByte();
            msg.skipBytes(protocolLength);

            //请求ID
            request.setRequestId(msg.readInt());

            //请求服务
            request.setServiceName(msg.readCharSequence(msg.readInt(), RPC_CHARSET).toString());

            //请求方法
            request.setMethodName(msg.readCharSequence(msg.readInt(), RPC_CHARSET).toString());

            //请求数据
            int dataLength = msg.readInt();
            if(dataLength > 0) {
                request.setData(new byte[dataLength]);
                msg.readBytes(request.getData());
            }else {
                request.setData(RpcUtil.EMPTY);
            }
            return pojo;
        }else if(pojo instanceof RpcResponse){
            RpcResponse response = (RpcResponse) pojo;

            //跳过协议头
            int protocolLength = msg.readByte();
            msg.skipBytes(protocolLength);

            //请求ID
            response.setRequestId(msg.readInt());

            //请求服务
            response.setStatus(msg.readInt());

            //数据是否已经编码
            response.setEncode(msg.readByte());

            //响应信息
            response.setMessage(msg.readCharSequence(msg.readInt(), RPC_CHARSET).toString());

            //请求数据
            int dataLength = msg.readInt();
            if(dataLength > 0) {
                response.setData(new byte[dataLength]);
                msg.readBytes(response.getData());
            }else {
                response.setData(RpcUtil.EMPTY);
            }
            return pojo;
        }else {
            return null;
        }
    }

}
