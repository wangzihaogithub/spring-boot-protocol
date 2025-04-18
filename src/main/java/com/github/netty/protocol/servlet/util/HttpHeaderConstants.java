package com.github.netty.protocol.servlet.util;

import io.netty.util.AsciiString;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author wangzihao
 */
public class HttpHeaderConstants {
    private static final Map<String, AsciiString> CACHE_HEADER_STRING_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, CharSequence>> CACHE_GROUP_HEADER_STRING_MAP = new ConcurrentHashMap<>();

    public static final CharSequence PATH = cacheAsciiString("Path");

    public static final CharSequence MAX_AGE_1 = cacheAsciiString("Max-Age");

    public static final CharSequence DOMAIN = cacheAsciiString("Domain");

    public static final CharSequence SECURE = cacheAsciiString("Secure");

    public static final CharSequence HTTPONLY = cacheAsciiString("HttpOnly");
    /**
     * {@code "Accept-Language"}
     */
    public static final CharSequence ACCEPT_LANGUAGE = cacheAsciiString("Accept-Language");
    /**
     * {@code "Connection"}
     */
    public static final CharSequence CONNECTION = cacheAsciiString("Connection");
    /**
     * {@code "Content-Language"}
     */
    public static final CharSequence CONTENT_LANGUAGE = cacheAsciiString("Content-Language");
    /**
     * {@code "Content-Length"}
     */
    public static final CharSequence CONTENT_LENGTH = cacheAsciiString("Content-Length");
    /**
     * {@code "Content-Type"}
     */
    public static final CharSequence CONTENT_TYPE = cacheAsciiString("Content-Type");
    /**
     * {@code "Cookie"}
     */
    public static final CharSequence COOKIE = cacheAsciiString("Cookie");
    /**
     * {@code "Date"}
     */
    public static final CharSequence DATE = cacheAsciiString("Date");
    /**
     * {@code "Expect"}
     */
    public static final CharSequence EXPECT = cacheAsciiString("Expect");
    /**
     * {@code "Host"}
     */
    public static final CharSequence HOST = cacheAsciiString("Host");
    /**
     * {@code "Location"}
     */
    public static final CharSequence LOCATION = cacheAsciiString("Location");
    /**
     * {@code "Sec-WebSocket-Key1"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY1 = cacheAsciiString("Sec-WebSocket-Key1");
    /**
     * {@code "Sec-WebSocket-Key2"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY2 = cacheAsciiString("Sec-WebSocket-Key2");
    /**
     * {@code "Sec-WebSocket-Location"}
     */
    public static final CharSequence SEC_WEBSOCKET_LOCATION = cacheAsciiString("Sec-WebSocket-Location");
    /**
     * {@code "Sec-WebSocket-Origin"}
     */
    public static final CharSequence SEC_WEBSOCKET_ORIGIN = cacheAsciiString("Sec-WebSocket-Origin");
    /**
     * {@code "Server"}
     */
    public static final CharSequence SERVER = cacheAsciiString("Server");
    /**
     * {@code "Set-Cookie"}
     */
    public static final CharSequence SET_COOKIE = cacheAsciiString("Set-Cookie");
    /**
     * {@code "TE"}
     */
    public static final CharSequence TE = cacheAsciiString("TE");
    /**
     * {@code "Trailer"}
     */
    public static final CharSequence TRAILER = cacheAsciiString("Trailer");
    /**
     * {@code "Transfer-Encoding"}
     */
    public static final CharSequence TRANSFER_ENCODING = cacheAsciiString("Transfer-Encoding");
    /**
     * {@code "charset"}
     */
    public static final CharSequence CHARSET = cacheAsciiString("charset");
    /**
     * {@code "chunked"}
     */
    public static final CharSequence CHUNKED = cacheAsciiString("chunked");
    /**
     * {@code "close"}
     */
    public static final CharSequence CLOSE = cacheAsciiString("close");
    /**
     * {@code "100-continue"}
     */
    public static final CharSequence CONTINUE = cacheAsciiString("100-continue");
    /**
     * {@code "keep-alive"}
     */
    public static final CharSequence KEEP_ALIVE = cacheAsciiString("keep-alive");
    /**
     * {@code "Sec-WebSocket-Extensions"}
     */
    public static final CharSequence SEC_WEBSOCKET_EXTENSIONS = cacheAsciiString("Sec-WebSocket-Extensions");
    /**
     * {@code "X-Forwarded-Port"}
     */
    public static final CharSequence X_FORWARDED_PORT = cacheAsciiString("X-Forwarded-Port");
    /**
     * {@code "X-Forwarded-Proto"}
     */
    public static final CharSequence X_FORWARDED_PROTO = cacheAsciiString("X-Forwarded-Proto");

    public static final CharSequence CONTENT_DISPOSITION = cacheAsciiString("Content-Disposition");
    public static final CharSequence NAME = cacheAsciiString("name");
    public static final CharSequence FILENAME = cacheAsciiString("filename");
    public static final CharSequence FORM_DATA = cacheAsciiString("form-data");

    public static CharSequence cacheAsciiString(String key) {
        return CACHE_HEADER_STRING_MAP.computeIfAbsent(key, AsciiString::new);
    }

    public static CharSequence cacheAsciiString(String key, String group, Supplier<CharSequence> supplier) {
        return CACHE_GROUP_HEADER_STRING_MAP.computeIfAbsent(group, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> supplier.get());
    }
}
