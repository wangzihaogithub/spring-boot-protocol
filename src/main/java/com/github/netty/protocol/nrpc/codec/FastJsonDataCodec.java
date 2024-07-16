package com.github.netty.protocol.nrpc.codec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcMethod;
import com.github.netty.protocol.nrpc.RpcServerInstance;
import com.github.netty.protocol.nrpc.exception.RpcDecodeException;
import com.github.netty.protocol.nrpc.exception.RpcEncodeException;
import io.netty.util.concurrent.FastThreadLocal;

import java.lang.reflect.Type;
import java.nio.charset.CharsetDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * com.alibaba.fastjson
 *
 * @author wangzihao
 */
public class FastJsonDataCodec implements DataCodec {
    private static final byte[] EMPTY = {};
    private static final FastThreadLocal<Map<String, Object>> PARAMETER_MAP_LOCAL = new FastThreadLocal<Map<String, Object>>() {
        @Override
        protected Map<String, Object> initialValue() throws Exception {
            return new LinkedHashMap<>(32);
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
    private static ParserConfig globalParserConfig = new ParserConfig();
    private static SerializeConfig globalSerializeConfig = new SerializeConfig();

    static {
        // Preheat code
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
    private SerializeConfig serializeConfig;
    private List<Consumer<Map<String, Object>>> encodeRequestConsumerList = new CopyOnWriteArrayList<>();
    private List<Consumer<Map<String, Object>>> decodeRequestConsumerList = new CopyOnWriteArrayList<>();

    public FastJsonDataCodec() {
        this(globalParserConfig, globalSerializeConfig);
    }

    public FastJsonDataCodec(ParserConfig parserConfig, SerializeConfig serializeConfig) {
        this.parserConfig = parserConfig;
        this.serializeConfig = serializeConfig;
    }

    public static SerializeConfig getGlobalSerializeConfig() {
        return globalSerializeConfig;
    }

    public static void setGlobalSerializeConfig(SerializeConfig globalSerializeConfig) {
        FastJsonDataCodec.globalSerializeConfig = globalSerializeConfig;
    }

    public static SerializerFeature[] getSerializerFeatures() {
        return SERIALIZER_FEATURES;
    }

    public static void setSerializerFeatures(SerializerFeature[] serializerFeatures) {
        SERIALIZER_FEATURES = serializerFeatures;
    }

    public static ParserConfig getGlobalParserConfig() {
        return globalParserConfig;
    }

    public static void setGlobalParserConfig(ParserConfig globalParserConfig) {
        FastJsonDataCodec.globalParserConfig = globalParserConfig;
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
                try {
                    return JSON.toJSONBytes(parameterMap, SERIALIZER_FEATURES);
                } catch (Exception e) {
                    throw new RpcEncodeException("encodeRequestData " + rpcMethod + " fastjson error " + e, e);
                }
            }
        } finally {
            parameterMap.clear();
        }
    }

    @Override
    public Object[] decodeRequestData(byte[] data, RpcMethod<RpcServerInstance> rpcMethod) {
        Map parameterMap;
        if (data != null && data.length != 0) {
            try {
                parameterMap = (Map) JSON.parse(data, 0, data.length, CHARSET_DECODER_LOCAL.get(), FEATURE_MASK);
            } catch (Exception e) {
                throw new RpcDecodeException("decodeRequestData " + rpcMethod + " fastjson error " + e, e);
            }
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
                if (value == null && name.length() > 1) {
                    String upperCaseName = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    value = parameterMap.get(upperCaseName);
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

        SerializeWriter out = new SerializeWriter();
        try {
            JSONSerializer serializer = new JSONSerializer(out, serializeConfig);
            serializer.write(data);
            return out.toBytes(CHARSET_UTF8.name());
        } catch (Exception e) {
            throw new RpcEncodeException("encodeResponseData " + rpcMethod + " fastjson error " + e, e);
        } finally {
            close(out);
        }
    }

    @Override
    public Object decodeResponseData(byte[] data, RpcMethod<RpcClient> rpcMethod) {
        if (data == null || data.length == 0) {
            return null;
        }
        Type returnType = rpcMethod.getGenericReturnType();
        try {
            return JSON.parseObject(data, returnType, FEATURES);
        } catch (Exception e) {
            throw new RpcDecodeException("decodeResponseData " + rpcMethod + " fastjson error " + e, e);
        }
    }

    @Override
    public Object decodeChunkResponseData(byte[] data, Type type) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return JSON.parseObject(data, type, FEATURES);
        } catch (Exception e) {
            throw new RpcDecodeException("decodeChunkResponseData " + type + " fastjson error " + e, e);
        }
    }

    @Override
    public byte[] encodeChunkResponseData(Object data) {
        if (data == null) {
            return EMPTY;
        }
        SerializeWriter out = new SerializeWriter();
        try {
            JSONSerializer serializer = new JSONSerializer(out, serializeConfig);
            serializer.write(data);
            return out.toBytes(CHARSET_UTF8.name());
        } catch (Exception e) {
            throw new RpcEncodeException("encodeChunkResponseData " + data.getClass() + " fastjson error " + e, e);
        } finally {
            close(out);
        }
    }

    protected boolean isNeedCast(Object value, Class<?> type) {
        if (value == null) {
            return false;
        }
        //The class information corresponding to type is the superclass or superinterface of the class information corresponding to arg object. Simply understood, type is the superclass or interface of arg
        return !type.isAssignableFrom(value.getClass());
    }

    protected Object cast(Object value, Class<?> type) {
        try {
            return TypeUtils.cast(value, type, parserConfig);
        } catch (Exception e) {
            return value;
        }
    }

    private static void close(Object out){
        if (out instanceof AutoCloseable) {
            try {
                ((AutoCloseable) out).close();
            } catch (Exception ignored) {

            }
        }
    }
}
