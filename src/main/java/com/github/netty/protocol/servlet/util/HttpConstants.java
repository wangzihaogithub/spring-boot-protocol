package com.github.netty.protocol.servlet.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author wangzihao
 *  2018/7/15/015
 */
public class HttpConstants {

    public static final String JSESSION_ID_COOKIE = "JSESSIONID";
    public static final String JSESSION_ID_URL = "jsessionid";

    public static final String POST = "POST";
    public static final String HTTPS = "https";
    public static final int HTTPS_PORT = 443;
    public static final int HTTP_PORT = 80;
    public static final String HTTP = "http";
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final String DEFAULT_SESSION_COOKIE_PATH = "/";

}
