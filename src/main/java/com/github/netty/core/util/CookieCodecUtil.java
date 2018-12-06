package com.github.netty.core.util;

import com.github.netty.register.servlet.util.HttpHeaderConstants;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpConstants;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by acer01 on 2018/8/5/005.
 */
public class CookieCodecUtil {

//    private static final Method COOKIE_DECODER_METHOD;
//    private static final Method COOKIE_ENCODER_METHOD;
//
//    static {
//        Method COOKIE_DECODER_METHOD_TEMP = null;
//        Method COOKIE_ENCODER_METHOD_TEMP = null;
//        Class cookieDecoderClass = ReflectUtil.forName(
//                "io.netty.handler.codec.http.CookieDecoder",
//                "io.netty.handler.codec.http.ServerCookieDecoder"
//        );
//        Class cookieEncoderClass = ReflectUtil.forName(
//                "io.netty.handler.codec.http.CookieEncoder",
//                "io.netty.handler.codec.http.ServerCookieEncoder"
//        );
//
//        if(cookieDecoderClass == null || cookieEncoderClass == null) {
//            throw new RuntimeException("netty版本不兼容");
//        }
//
//        try {
//            COOKIE_DECODER_METHOD_TEMP = cookieDecoderClass.getDeclaredMethod("decode",String.class);
//            if(COOKIE_DECODER_METHOD_TEMP == null){
//                throw new RuntimeException("netty版本不兼容");
//            }
//
//            COOKIE_ENCODER_METHOD_TEMP = cookieEncoderClass.getDeclaredMethod("encode", io.netty.handler.codec.http.Cookie.class);
//            if(COOKIE_ENCODER_METHOD_TEMP == null){
//                throw new RuntimeException("netty版本不兼容");
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("netty版本不兼容");
//        }
//
//        COOKIE_DECODER_METHOD = COOKIE_DECODER_METHOD_TEMP;
//        COOKIE_ENCODER_METHOD = COOKIE_ENCODER_METHOD_TEMP;
//    }

    /**
     * Decodes the specified Set-Cookie HTTP header value into a {@link Cookie}.
     * @return the decoded {@link Cookie}
     */
    public static Set<Cookie> decode(String header) {
//        Collection<io.netty.handler.codec.http.Cookie> nettyCookieSet = (Collection<io.netty.handler.codec.http.Cookie>) ReflectUtil.invokeMethod(null,COOKIE_DECODER_METHOD,value);

        if (header == null) {
            throw new NullPointerException("header");
        }

        final int headerLen = header.length();

        if (headerLen == 0) {
            return Collections.emptySet();
        }

        Set<Cookie> cookies = new TreeSet<Cookie>();

        int i = 0;

        boolean rfc2965Style = false;
        if (header.regionMatches(true, 0, "$Version", 0, 8)) {
            // RFC 2965 style cookie, move to after version value
            i = header.indexOf(';') + 1;
            rfc2965Style = true;
        }

        loop: for (;;) {

            // Skip spaces and separators.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                char c = header.charAt(i);
                if (c == '\t' || c == '\n' || c == 0x0b || c == '\f'
                        || c == '\r' || c == ' ' || c == ',' || c == ';') {
                    i++;
                    continue;
                }
                break;
            }

            int newNameStart = i;
            int newNameEnd = i;
            String value;

            if (i == headerLen) {
                value = null;
            } else {
                keyValLoop: for (;;) {

                    char curChar = header.charAt(i);
                    if (curChar == ';') {
                        // NAME; (no value till ';')
                        newNameEnd = i;
                        value = null;
                        break keyValLoop;
                    } else if (curChar == '=') {
                        // NAME=VALUE
                        newNameEnd = i;
                        i++;
                        if (i == headerLen) {
                            // NAME= (empty value, i.e. nothing after '=')
                            value = "";
                            break keyValLoop;
                        }

                        int newValueStart = i;
                        char c = header.charAt(i);
                        if (c == '"') {
                            // NAME="VALUE"
                            StringBuilder newValueBuf = stringBuilder();

                            final char q = c;
                            boolean hadBackslash = false;
                            i++;
                            for (;;) {
                                if (i == headerLen) {
                                    value = newValueBuf.toString();
                                    break keyValLoop;
                                }
                                if (hadBackslash) {
                                    hadBackslash = false;
                                    c = header.charAt(i++);
                                    if (c == '\\' || c == '"') {
                                        // Escape last backslash.
                                        newValueBuf.setCharAt(newValueBuf.length() - 1, c);
                                    } else {
                                        // Do not escape last backslash.
                                        newValueBuf.append(c);
                                    }
                                } else {
                                    c = header.charAt(i++);
                                    if (c == q) {
                                        value = newValueBuf.toString();
                                        break keyValLoop;
                                    }
                                    newValueBuf.append(c);
                                    if (c == '\\') {
                                        hadBackslash = true;
                                    }
                                }
                            }
                        } else {
                            // NAME=VALUE;
                            int semiPos = header.indexOf(';', i);
                            if (semiPos > 0) {
                                value = header.substring(newValueStart, semiPos);
                                i = semiPos;
                            } else {
                                value = header.substring(newValueStart);
                                i = headerLen;
                            }
                        }
                        break keyValLoop;
                    } else {
                        i++;
                    }

                    if (i == headerLen) {
                        // NAME (no value till the end of string)
                        newNameEnd = headerLen;
                        value = null;
                        break;
                    }
                }
            }

            if (!rfc2965Style || (!header.regionMatches(newNameStart, "$Path", 0, "$Path".length()) &&
                    !header.regionMatches(newNameStart, "$Domain", 0, "$Domain".length()) &&
                    !header.regionMatches(newNameStart, "$Port", 0, "$Port".length()))) {

                // skip obsolete RFC2965 fields
                String name = header.substring(newNameStart, newNameEnd);
                cookies.add(new DefaultCookie(name, value));
            }
        }

        return cookies;
    }


    static StringBuilder stringBuilder() {
        return RecyclableUtil.newStringBuilder();
    }

    /**
     * @param buf a buffer where some cookies were maybe encoded
     * @return the buffer String without the trailing separator, or null if no cookie was appended.
     */
    static String stripTrailingSeparatorOrNull(StringBuilder buf) {
        return buf.length() == 0 ? null : stripTrailingSeparator(buf);
    }

    static String stripTrailingSeparator(StringBuilder buf) {
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 2);
        }
        return buf.toString();
    }

    static void addUnquoted(StringBuilder sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append(val);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    static void add(StringBuilder sb, String name, long val) {
        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append(val);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    public static String encode(Cookie cookie) {
//        String value = (String) ReflectUtil.invokeMethod(null,COOKIE_ENCODER_METHOD,cookie);

        if (cookie == null) {
            throw new NullPointerException("cookie");
        }

        StringBuilder buf = stringBuilder();

        addUnquoted(buf, cookie.getName(), cookie.getValue());

        if (cookie.getMaxAge() > 0) {
            add(buf, HttpHeaderConstants.MAX_AGE_1.toString(), cookie.getMaxAge());
//            Date expires = new Date(cookie.maxAge() * 1000 + System.currentTimeMillis());
//            addUnquoted(buf, HttpHeaderConstants.EXPIRES.toString(), HttpHeaderDateFormat.get().format(expires));
        }

        if (cookie.getPath() != null) {
            addUnquoted(buf, HttpHeaderConstants.PATH.toString(), cookie.getPath());
        }

        if (cookie.getDomain() != null) {
            addUnquoted(buf, HttpHeaderConstants.DOMAIN.toString(), cookie.getDomain());
        }
        if (cookie.isSecure()) {
            buf.append(HttpHeaderConstants.SECURE);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (cookie.isHttpOnly()) {
            buf.append(HttpHeaderConstants.HTTPONLY);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }

        return stripTrailingSeparator(buf);
    }

}
