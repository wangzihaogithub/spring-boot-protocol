package com.github.netty.protocol.nrpc.codec;

import com.github.netty.core.util.SystemPropertyUtil;

public class DataCodecUtil {
    public static final String SYSTEM_PROPERTY_CODEC_KEY = "netty-nrpc.codec";
    private static final boolean EXIST_FASTJSON;
    private static final boolean EXIST_JACKSON;

    static {
        boolean existFastJson;
        boolean existJackson;
        try {
            Class.forName("com.alibaba.fastjson.JSON");
            existFastJson = true;
        } catch (Throwable e) {
            existFastJson = false;
        }
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            existJackson = true;
        } catch (Throwable e) {
            existJackson = false;
        }
        EXIST_FASTJSON = existFastJson;
        EXIST_JACKSON = existJackson;
    }

    public static String buildThrowableRpcMessage(Throwable throwable) {
        String message = getMessage(throwable);
        Throwable cause = getCause(throwable);
        if (cause != null && cause != throwable) {
            message = message + ". cause=" + getMessage(cause);
        }
        return message;
    }

    private static Throwable getCause(Throwable throwable) {
        if (throwable == null || throwable.getCause() == null) {
            return null;
        }
        while (true) {
            Throwable cause = throwable;
            throwable = throwable.getCause();
            if (throwable == null) {
                return cause;
            }
        }
    }

    private static String getMessage(Throwable t) {
        String message = t.getMessage();
        return message == null ? t.toString() : message;
    }

    public static String getDataCodec() {
        String codec = SystemPropertyUtil.get(SYSTEM_PROPERTY_CODEC_KEY);
        if (codec != null && codec.length() > 0) {
            return codec;
        } else {
            return "jdk";
        }
    }

    /**
     * set codec
     *
     * @param codec [fastjson,jackson,jdk,auto]
     */
    public static void setDataCodec(String codec) {
        System.setProperty(SYSTEM_PROPERTY_CODEC_KEY, codec);
    }

    public static DataCodec newDataCodec() {
        DataCodec dataCodec;
        String codec = SystemPropertyUtil.get(SYSTEM_PROPERTY_CODEC_KEY);
        if (codec != null && codec.length() > 0) {
            switch (codec) {
                case "fastjson": {
                    dataCodec = new FastJsonDataCodec();
                    break;
                }
                case "jackson": {
                    dataCodec = new JacksonDataCodec();
                    break;
                }
                default:
                case "jdk": {
                    dataCodec = new JdkDataCodec();
                    break;
                }
                case "auto": {
                    if (EXIST_FASTJSON) {
                        dataCodec = new FastJsonDataCodec();
                    } else if (EXIST_JACKSON) {
                        dataCodec = new JacksonDataCodec();
                    } else {
                        dataCodec = new JdkDataCodec();
                    }
                    break;
                }
            }
        } else {
            dataCodec = new JdkDataCodec();
        }
        return dataCodec;
    }
}
