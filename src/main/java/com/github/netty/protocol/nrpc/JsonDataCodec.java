package com.github.netty.protocol.nrpc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.HashMap;
import java.util.Map;

/**
 * @author wangzihao
 */
public class JsonDataCodec implements DataCodec {
    private static SerializerFeature[] SERIALIZER_FEATURES = {
//            SerializerFeature.WriteClassName
    };

    private FastThreadLocal<Map<String,Object>> parameterMapLocal = new FastThreadLocal<Map<String,Object>>(){
        @Override
        protected Map<String,Object> initialValue() throws Exception {
            return new HashMap<>(32);
        }
    };
    private ParserConfig parserConfig;

    public JsonDataCodec() {
        this(new ParserConfig());
    }

    public JsonDataCodec(ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
    }

    public static void setSerializerFeatures(SerializerFeature[] serializerFeatures) {
        SERIALIZER_FEATURES = serializerFeatures;
    }

    public static SerializerFeature[] getSerializerFeatures() {
        return SERIALIZER_FEATURES;
    }

    @Override
    public ByteBuf encodeRequestData(Object[] data,RpcMethod rpcMethod) {
        if(data == null || data.length == 0){
            return Unpooled.EMPTY_BUFFER;
        }

        String[] parameterNames = rpcMethod.getParameterNames();
        Map<String, Object> parameterMap = parameterMapLocal.get();
        try {
            for (int i = 0; i < parameterNames.length; i++) {
                String name = parameterNames[i];
                if (name == null) {
                    continue;
                }
                Object value = data[i];
                parameterMap.put(name, value);
            }
            return RecyclableUtil.newReadOnlyBuffer(JSON.toJSONBytes(parameterMap, SERIALIZER_FEATURES));
        }finally {
            parameterMap.clear();
        }
    }

    @Override
    public Object[] decodeRequestData(ByteBuf data, RpcMethod rpcMethod) {
        if(data == null || data.readableBytes() == 0){
            return null;
        }

        String[] parameterNames = rpcMethod.getParameterNames();
        Object[] parameterValues = new Object[parameterNames.length];
        Class<?>[] parameterTypes = rpcMethod.getMethod().getParameterTypes();

        String json = data.toString(CHARSET_UTF8);
        Map parameterMap = (Map) JSON.parse(json,JSON.DEFAULT_PARSER_FEATURE);

        for(int i =0; i<parameterNames.length; i++){
            Class<?> type = parameterTypes[i];
            String name = parameterNames[i];
            Object value = parameterMap.get(name);

            if(isNeedCast(value,type)){
                value = cast(value, type);
            }
            parameterValues[i] = value;
        }
        return parameterValues;
    }

    @Override
    public ByteBuf encodeResponseData(Object data) {
        if(data == null){
            return Unpooled.EMPTY_BUFFER;
        }
        return Unpooled.wrappedBuffer(JSON.toJSONBytes(data,SERIALIZER_FEATURES));
    }

    @Override
    public Object decodeResponseData(ByteBuf data) {
        if(data == null || data.readableBytes() == 0){
            return null;
        }
        return JSON.parse(data.toString(CHARSET_UTF8));
    }

    protected boolean isNeedCast(Object value,Class<?> type){
        if(value == null){
            return false;
        }
        //The class information corresponding to type is the superclass or superinterface of the class information corresponding to arg object. Simply understood, type is the superclass or interface of arg
        if(type.isAssignableFrom(value.getClass())){
            return false;
        }
        return true;
    }

    protected Object cast(Object value, Class<?> type) {
        try {
            return TypeUtils.cast(value,type,parserConfig);
        }catch (Exception e){
            return value;
        }
    }

}
