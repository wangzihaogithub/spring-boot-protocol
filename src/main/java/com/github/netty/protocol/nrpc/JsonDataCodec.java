package com.github.netty.protocol.nrpc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;
import io.netty.util.concurrent.FastThreadLocal;

import java.lang.reflect.Type;
import java.nio.charset.CharsetDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * @author wangzihao
 */
public class JsonDataCodec implements DataCodec {
    private static final byte[] EMPTY = {};
    private static final FastThreadLocal<Map<String, Object>> PARAMETER_MAP_LOCAL = new FastThreadLocal<Map<String, Object>>() {
        @Override
        protected Map<String, Object> initialValue() throws Exception {
            return new HashMap<>(32);
        }
    };
    private static final FastThreadLocal<CharsetDecoder> CHARSET_DECODER_LOCAL = new FastThreadLocal<CharsetDecoder>() {
        @Override
        protected CharsetDecoder initialValue() throws Exception {
            return CHARSET_UTF8.newDecoder();
        }
    };
    private static SerializerFeature[] SERIALIZER_FEATURES = {
//            SerializerFeature.WriteClassName
    };
    private static Feature[] FEATURES = {
            Feature.AutoCloseSource,
            Feature.InternFieldNames,
            Feature.UseBigDecimal,
            Feature.AllowUnQuotedFieldNames,
            Feature.AllowSingleQuotes,
            Feature.AllowArbitraryCommas,
            Feature.SortFeidFastMatch,
            Feature.IgnoreNotMatch
    };
    private static int FEATURE_MASK = Feature.of(FEATURES);

    static {
        try {
            Class.forName("com.alibaba.fastjson.util.TypeUtils");
            Class.forName("com.alibaba.fastjson.JSON");
            Class.forName("com.alibaba.fastjson.util.ASMClassLoader");
            Class.forName("com.alibaba.fastjson.util.IOUtils");
        } catch (ClassNotFoundException e) {
            //
        }
    }

    private ParserConfig parserConfig;
    private List<Consumer<Map<String, Object>>> encodeRequestConsumerList = new CopyOnWriteArrayList<>();
    private List<Consumer<Map<String, Object>>> decodeRequestConsumerList = new CopyOnWriteArrayList<>();

    public JsonDataCodec() {
        this(new ParserConfig());
    }

    public JsonDataCodec(ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
    }

    public static SerializerFeature[] getSerializerFeatures() {
        return SERIALIZER_FEATURES;
    }

    public static void setSerializerFeatures(SerializerFeature[] serializerFeatures) {
        SERIALIZER_FEATURES = serializerFeatures;
    }

    @Override
    public List<Consumer<Map<String, Object>>> getEncodeRequestConsumerList() {
        return encodeRequestConsumerList;
    }

    @Override
    public List<Consumer<Map<String, Object>>> getDecodeRequestConsumerList() {
        return decodeRequestConsumerList;
    }

    @Override
    public byte[] encodeRequestData(Object[] data, RpcMethod<RpcClient> rpcMethod) {
        String[] parameterNames = rpcMethod.getParameterNames();
        Map<String, Object> parameterMap = PARAMETER_MAP_LOCAL.get();
        if (data != null && data.length != 0) {
            for (int i = 0; i < parameterNames.length; i++) {
                String name = parameterNames[i];
                if (name == null) {
                    continue;
                }
                Object value = data[i];
                parameterMap.put(name, value);
            }
        }

        try {
            for (Consumer<Map<String, Object>> consumer : encodeRequestConsumerList) {
                consumer.accept(parameterMap);
            }
            if (parameterMap.isEmpty()) {
                return EMPTY;
            } else {
                return JSON.toJSONBytes(parameterMap, SERIALIZER_FEATURES);
            }
        } finally {
            parameterMap.clear();
        }
    }

    @Override
    public Object[] decodeRequestData(byte[] data, RpcMethod<RpcServerInstance> rpcMethod) {
        Map parameterMap;
        if (data != null && data.length != 0) {
            parameterMap = (Map) JSON.parse(data, 0, data.length, CHARSET_DECODER_LOCAL.get(), FEATURE_MASK);
        } else {
            parameterMap = PARAMETER_MAP_LOCAL.get();
        }
        try {
            for (Consumer<Map<String, Object>> consumer : decodeRequestConsumerList) {
                consumer.accept(parameterMap);
            }

            String[] parameterNames = rpcMethod.getParameterNames();
            Object[] parameterValues = new Object[parameterNames.length];
            Class<?>[] parameterTypes = rpcMethod.getParameterTypes();
            for (int i = 0; i < parameterNames.length; i++) {
                Class<?> type = parameterTypes[i];
                String name = parameterNames[i];
                Object value = parameterMap.get(name);
                if (value == null && !parameterMap.containsKey(name)) {
                    value = parameterMap.get("arg" + i);
                }
                if (isNeedCast(value, type)) {
                    value = cast(value, type);
                }
                parameterValues[i] = value;
            }
            return parameterValues;
        } finally {
            parameterMap.clear();
        }
    }

    @Override
    public byte[] encodeResponseData(Object data, RpcMethod<RpcServerInstance> rpcMethod) {
        if (data == null) {
            return EMPTY;
        }

        try (SerializeWriter out = new SerializeWriter(null, JSON.DEFAULT_GENERATE_FEATURE,
                SERIALIZER_FEATURES)) {
            JSONSerializer serializer = new JSONSerializer(out, SerializeConfig.globalInstance);
            serializer.write(data);
            return out.toBytes(CHARSET_UTF8);
        }
    }

    @Override
    public Object decodeResponseData(byte[] data, RpcMethod<RpcClient> rpcMethod) {
        if (data == null || data.length == 0) {
            return null;
        }
        Type returnType = rpcMethod.getGenericReturnType();
        return JSON.parseObject(data, 0, data.length, CHARSET_UTF8, returnType, FEATURES);
    }

    protected boolean isNeedCast(Object value, Class<?> type) {
        if (value == null) {
            return false;
        }
        //The class information corresponding to type is the superclass or superinterface of the class information corresponding to arg object. Simply understood, type is the superclass or interface of arg
        if (type.isAssignableFrom(value.getClass())) {
            return false;
        }
        return true;
    }

    protected Object cast(Object value, Class<?> type) {
        try {
            return TypeUtils.cast(value, type, parserConfig);
        } catch (Exception e) {
            return value;
        }
    }

}
