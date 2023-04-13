package com.github.netty.protocol.nrpc.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.github.netty.core.util.TypeUtil;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcMethod;
import com.github.netty.protocol.nrpc.RpcServerInstance;
import com.github.netty.protocol.nrpc.exception.RpcDecodeException;
import com.github.netty.protocol.nrpc.exception.RpcEncodeException;
import io.netty.util.concurrent.FastThreadLocal;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * com.fasterxml.jackson
 *
 * @author wangzihao
 */
public class JacksonDataCodec implements DataCodec {
    private static final byte[] EMPTY = {};
    private static final FastThreadLocal<Map<String, Object>> PARAMETER_MAP_LOCAL = new FastThreadLocal<Map<String, Object>>() {
        @Override
        protected Map<String, Object> initialValue() throws Exception {
            return new LinkedHashMap<>(32);
        }
    };
    private static ObjectMapper globalObjectMapper = new ObjectMapper();
    private List<Consumer<Map<String, Object>>> encodeRequestConsumerList = new CopyOnWriteArrayList<>();
    private List<Consumer<Map<String, Object>>> decodeRequestConsumerList = new CopyOnWriteArrayList<>();
    private ObjectMapper objectMapper;

    public JacksonDataCodec() {
        this(globalObjectMapper);
    }

    public JacksonDataCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static ObjectMapper getGlobalObjectMapper() {
        return globalObjectMapper;
    }

    public static void setGlobalObjectMapper(ObjectMapper globalObjectMapper) {
        JacksonDataCodec.globalObjectMapper = globalObjectMapper;
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
                    return objectMapper.writeValueAsBytes(parameterMap);
                } catch (Exception e) {
                    throw new RpcEncodeException("encodeRequestData " + rpcMethod + " jackson error " + e, e);
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
                parameterMap = objectMapper.readValue(data, LinkedHashMap.class);
            } catch (Exception e) {
                throw new RpcDecodeException("decodeRequestData " + rpcMethod + " jackson error " + e, e);
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
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RpcEncodeException("encodeResponseData " + rpcMethod + " jackson error " + e, e);
        }
    }

    @Override
    public Object decodeResponseData(byte[] data, RpcMethod<RpcClient> rpcMethod) {
        if (data == null || data.length == 0) {
            return null;
        }

        Type returnType = rpcMethod.getGenericReturnType();
        try {
            return objectMapper.readValue(data, TypeFactory.defaultInstance().constructType(returnType));
        } catch (Exception e) {
            throw new RpcDecodeException("decodeResponseData " + rpcMethod + " jackson error " + e, e);
        }
    }

    @Override
    public Object decodeChunkResponseData(byte[] data, Type type) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            return objectMapper.readValue(data, TypeFactory.defaultInstance().constructType(type));
        } catch (Exception e) {
            throw new RpcDecodeException("decodeChunkResponseData " + type + " jackson error " + e, e);
        }
    }

    @Override
    public byte[] encodeChunkResponseData(Object data) {
        if (data == null) {
            return EMPTY;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RpcEncodeException("encodeChunkResponseData " + data.getClass() + " jackson error " + e, e);
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
            return TypeUtil.cast(value, type);
        } catch (Exception e) {
            return value;
        }
    }

}
