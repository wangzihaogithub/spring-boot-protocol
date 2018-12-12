package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.github.netty.core.util.IOUtil.INT_LENGTH;


/**
 * RPC 编码器
 * @author 84215
 */
@ChannelHandler.Sharable
public class RpcEncoder extends MessageToByteEncoder {

    public static final byte[] PROTOCOL_HEADER = new byte[]{'N','R','P','C',0,0,0,0};
    public static final byte[] END_DELIMITER = new byte[]{'E','N','D','\r','\n'};
    public static final Charset RPC_CHARSET = StandardCharsets.UTF_8;

    public RpcEncoder() {}

    @Override
    public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {
        if(in instanceof RpcRequest){
            RpcRequest request = (RpcRequest) in;
            int writeLength;

            //协议头
            out.writeByte(PROTOCOL_HEADER.length);
            out.writeBytes(PROTOCOL_HEADER);

            //请求ID
            out.writeInt(request.getRequestId());

            //请求服务
            out.writerIndex(out.writerIndex() + INT_LENGTH);
            writeLength = out.writeCharSequence(request.getServiceName(), RPC_CHARSET);
            out.setInt(out.writerIndex() - writeLength - INT_LENGTH,writeLength);

            //请求方法
            out.writerIndex(out.writerIndex() + INT_LENGTH);
            writeLength = out.writeCharSequence(request.getMethodName(), RPC_CHARSET);
            out.setInt(out.writerIndex() - writeLength - INT_LENGTH,writeLength);

            //请求数据
            byte[] data = request.getData();
            out.writeInt(data.length);
            if(data.length > 0){
                out.writeBytes(data);
            }

            //结束符
            out.writeBytes(END_DELIMITER);
        }else if(in instanceof RpcResponse){
            RpcResponse response = (RpcResponse) in;
            int writeLength;

            //协议头
            out.writeByte(PROTOCOL_HEADER.length);
            out.writeBytes(PROTOCOL_HEADER);

            //请求ID
            out.writeInt(response.getRequestId());
            //响应状态
            out.writeInt(response.getStatus());
            //数据是否已经编码
            out.writeByte(response.getEncode());

            //响应信息
            out.writerIndex(out.writerIndex() + INT_LENGTH);
            writeLength = out.writeCharSequence(response.getMessage(), RPC_CHARSET);
            out.setInt(out.writerIndex() - writeLength - INT_LENGTH,writeLength);

            //响应数据
            byte[] data = response.getData();
            out.writeInt(data.length);
            if(data.length > 0) {
                out.writeBytes(data);
            }

            //结束符
            out.writeBytes(END_DELIMITER);
        }
    }

}
