package com.github.netty.register.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import io.protostuff.ByteArrayInput;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.github.netty.register.rpc.RpcEncoder.END_DELIMITER;
import static com.github.netty.register.rpc.RpcEncoder.PROTOCOL_HEADER;

/**
 *  RPC 解码器
 * @author 84215
 */
public class RpcDecoder extends DelimiterBasedFrameDecoder {

    private static final Map<Class<?>, Schema<?>> CACHE_SCHEMA_MAP = new ConcurrentHashMap<>();
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
        if(msg == null){
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
     * @throws IOException
     */
    private Object decodeToPojo(ByteBuf msg) throws IOException {
        if(!RpcUtil.isRpcProtocols(msg)){
            return null;
        }
        msg.skipBytes(PROTOCOL_HEADER.length());

        int dataLength = msg.readInt();
        byte[] data = new byte[dataLength];
        msg.readBytes(data);
        ByteArrayInput input = new ByteArrayInput(data, false);

        Object pojo = pojoSupplier.get();
        Schema schema = getSchema(pojo.getClass());
        schema.mergeFrom(input, pojo);
        return pojo;
    }

    private static <T> Schema<T> getSchema(Class<T> cls) {
        Schema<T> schema = (Schema<T>) CACHE_SCHEMA_MAP.get(cls);
        if (schema == null) {
            schema = RuntimeSchema.createFrom(cls);
            CACHE_SCHEMA_MAP.put(cls, schema);
        }
        return schema;
    }
}
