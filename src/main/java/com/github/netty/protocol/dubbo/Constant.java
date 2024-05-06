package com.github.netty.protocol.dubbo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Constant {
    // header length.
    public static final int HEADER_LENGTH = 16;
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    public static final byte RESPONSE_VALUE = 1;
    public static final byte RESPONSE_NULL_VALUE = 2;
    public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;
    /**
     * ok.
     */
    public static final byte OK = 20;
    //    /**
//     * client side timeout.
//     */
//    public static final byte CLIENT_TIMEOUT = 30;
//    /**
//     * server side timeout.
//     */
//    public static final byte SERVER_TIMEOUT = 31;
    /**
     * channel inactive, directly return the unfinished requests.
     */
    public static final byte CHANNEL_INACTIVE = 35;
    //    /**
//     * request format error.
//     */
//    public static final byte BAD_REQUEST = 40;
//    /**
//     * response format error.
//     */
//    public static final byte BAD_RESPONSE = 50;
    /**
     * service not found.
     */
    public static final byte SERVICE_NOT_FOUND = 60;
    /**
     * service error.
     */
    public static final byte SERVICE_ERROR = 70;
//    /**
//     * internal server error.
//     */
//    public static final byte SERVER_ERROR = 80;
//    /**
//     * internal server error.
//     */
//    public static final byte CLIENT_ERROR = 90;
//    /**
//     * server side threadpool exhausted and quick return.
//     */
//    public static final byte SERVER_THREADPOOL_EXHAUSTED_ERROR = 100;
    public static final String JAVA_IDENT_REGEX = "(?:[_$a-zA-Z][_$a-zA-Z0-9]*)";
    public static final String CLASS_DESC = "(?:L" + JAVA_IDENT_REGEX + "(?:\\/" + JAVA_IDENT_REGEX + ")*;)";
    public static final String ARRAY_DESC = "(?:\\[+(?:(?:[VZBCDFIJS])|" + CLASS_DESC + "))";
    public static final String DESC_REGEX = "(?:(?:[VZBCDFIJS])|" + CLASS_DESC + "|" + ARRAY_DESC + ")";
    public static final Pattern DESC_PATTERN = Pattern.compile(DESC_REGEX);
    // magic header.
    protected static final short MAGIC = (short) 0xdabb;
    protected static final byte MAGIC_0 = (byte) (MAGIC >>> 8);
    protected static final byte MAGIC_1 = (byte) MAGIC;
    // message flag.
    protected static final byte FLAG_REQUEST = (byte) 0x80;
    //    protected static final byte FLAG_TWOWAY = (byte) 0x40;
    protected static final byte FLAG_EVENT = (byte) 0x20;
    protected static final int SERIALIZATION_MASK = 0x1f;

    public static int countArgs(String desc) {
        int length = desc.length();
        switch (length) {
            case 0:
            case 1: {
                return length;
            }
            default: {
                int count = 0;
                Matcher m = DESC_PATTERN.matcher(desc);
                while (m.find()) {
                    count++;
                }
                return count;
            }
        }
    }
}
