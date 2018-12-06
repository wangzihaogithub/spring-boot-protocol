package com.github.netty.register.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * RPC 编码器
 * @author 84215
 */
@ChannelHandler.Sharable
public class RpcEncoder extends MessageToByteEncoder {

    public static final String PROTOCOL_HEADER = new String(new char[]{'N','R','P','C'});
    public static final byte[] END_DELIMITER = new byte[]{'E','N','D','\r','\n'};

    private Class<?> genericClass;
    private static final Map<Class<?>, Schema<?>> CACHE_SCHEMA_MAP = new ConcurrentHashMap<>();

    public RpcEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {
        if (!genericClass.isInstance(in)) {
            return;
        }

        Class cls = in.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            Schema schema = getSchema(cls);
            byte[] data = ProtostuffIOUtil.toByteArray(in, schema, buffer);

            writeHeader(out);
            out.writeInt(data.length);
            out.writeBytes(data);
            out.writeBytes(END_DELIMITER);
        }finally {
            buffer.clear();
        }
    }

    private void writeHeader(ByteBuf byteBuf){
        for(int i = 0; i< PROTOCOL_HEADER.length(); i++){
            byteBuf.writeByte(PROTOCOL_HEADER.charAt(i));
        }
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
