package com.github.netty.protocol.servlet.util;

import io.netty.util.AsciiString;

import java.nio.charset.Charset;

/**
 * @author wangzihao
 * 2018/7/15/015
 */
public class HttpConstants {

    public static final String JSESSION_ID_COOKIE = "JSESSIONID";
    public static final String JSESSION_ID_URL = "jsessionid";

    public static final String HTTPS = "https";
    public static final int HTTPS_PORT = 443;
    public static final int HTTP_PORT = 80;
    public static final String HTTP = "http";
    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    public static final String DEFAULT_SESSION_COOKIE_PATH = "/";
    public static final AsciiString H2_EXT_STREAM_ID = AsciiString.cached("x-http2-stream-id");
    public static final AsciiString H2_EXT_SCHEME = AsciiString.cached("x-http2-scheme");
    public static final boolean EXIST_DEPENDENCY_H2;

    static {
        boolean isExistH2;
        try {
            Class.forName("io.netty.handler.codec.http2.Http2ConnectionHandler");
            isExistH2 = true;
        } catch (Throwable e) {
            isExistH2 = false;
        }
        EXIST_DEPENDENCY_H2 = isExistH2;
    }

}
