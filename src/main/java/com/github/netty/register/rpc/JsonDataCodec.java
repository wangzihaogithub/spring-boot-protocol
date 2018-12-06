package com.github.netty.register.rpc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 84215
 */
public class JsonDataCodec implements DataCodec {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final byte[] EMPTY = new byte[0];
    private static SerializerFeature[] SERIALIZER_FEATURES = {
//            SerializerFeature.WriteClassName
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
    public byte[] encodeRequestData(Object[] data,RpcMethod rpcMethod) {
        if(data == null || data.length == 0){
            return EMPTY;
        }

        String[] parameterNames = rpcMethod.getParameterNames();
        Map<String,Object> parameterMap = new HashMap<>(8);
        for (int i=0; i<parameterNames.length; i++) {
            String name = parameterNames[i];
            if(name == null){
                continue;
            }
            Object value = data[i];
            parameterMap.put(name, value);
        }
        return JSON.toJSONBytes(parameterMap,SERIALIZER_FEATURES);
    }

    @Override
    public Object[] decodeRequestData(byte[] data, RpcMethod rpcMethod) {
        if(data == null || data.length == 0){
            return null;
        }

        String[] parameterNames = rpcMethod.getParameterNames();
        Object[] parameterValues = new Object[parameterNames.length];
        Class<?>[] parameterTypes = rpcMethod.getMethod().getParameterTypes();
        Map parameterMap = (Map) JSON.parse(data,0,data.length,UTF8.newDecoder(),JSON.DEFAULT_PARSER_FEATURE);

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
    public byte[] encodeResponseData(Object data) {
        if(data == null){
            return EMPTY;
        }
        return JSON.toJSONBytes(data,SERIALIZER_FEATURES);
    }

    @Override
    public Object decodeResponseData(byte[] data) {
        if(data == null || data.length == 0){
            return null;
        }
        return JSON.parse(data);
    }

    protected boolean isNeedCast(Object value,Class<?> type){
        if(value == null){
            return false;
        }
        //type 所对应类信息是arg对象所对应的类信息的父类或者是父接口，简单理解即type是arg的父类或接口
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
