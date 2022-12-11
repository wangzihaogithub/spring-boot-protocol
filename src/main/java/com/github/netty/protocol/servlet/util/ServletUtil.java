package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.LinkedMultiValueMap;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.servlet.ServletHttpServletRequest;
import com.github.netty.protocol.servlet.ServletHttpServletResponse;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.RecyclableArrayList;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.Cookie;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ServletUtil
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletUtil {
    private static final String EMPTY_STRING = "";
    private static final String CHARSET_APPEND = HttpHeaderConstants.CHARSET + "=";
    private static final char SPACE = ' ';
    private static final Cookie[] EMPTY_COOKIE = {};
    private static byte[] HEX2B;
    private static long lastTimestamp = System.currentTimeMillis();
    private static final Date lastDate = new Date(lastTimestamp);
    private static String nowRFCTime = DateFormatter.format(lastDate);

    private static void initHex2bIfHaveMemory() {
        if (HEX2B != null) {
            return;
        }
        try {
            synchronized (ServletUtil.class) {
                if (HEX2B == null) {
                    HEX2B = new byte[65536];
                }
            }
        } catch (OutOfMemoryError e) {
            return;
        }
        Arrays.fill(HEX2B, (byte) -1);
        HEX2B[48] = 0;
        HEX2B[49] = 1;
        HEX2B[50] = 2;
        HEX2B[51] = 3;
        HEX2B[52] = 4;
        HEX2B[53] = 5;
        HEX2B[54] = 6;
        HEX2B[55] = 7;
        HEX2B[56] = 8;
        HEX2B[57] = 9;
        HEX2B[65] = 10;
        HEX2B[66] = 11;
        HEX2B[67] = 12;
        HEX2B[68] = 13;
        HEX2B[69] = 14;
        HEX2B[70] = 15;
        HEX2B[97] = 10;
        HEX2B[98] = 11;
        HEX2B[99] = 12;
        HEX2B[100] = 13;
        HEX2B[101] = 14;
        HEX2B[102] = 15;
    }

    public static String getDateByRfcHttp() {
        long timestamp = System.currentTimeMillis();
        //cache 1/s
        if (timestamp - lastTimestamp > 1000L) {
            lastTimestamp = timestamp;
            lastDate.setTime(timestamp);
            nowRFCTime = DateFormatter.format(lastDate);
        }
        return nowRFCTime;
    }

    public static String getCookieValue(Cookie[] cookies, String cookieName) {
        if (cookies == null || cookieName == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }

            String name = cookie.getName();
            if (cookieName.equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void decodeByUrl(LinkedMultiValueMap<String, String> parameterMap, String uri, Charset charset) {
        decodeParams(parameterMap, uri, findPathEndIndex(uri), charset);
    }

    public static void decodeByUrl(Map<String, String[]> sourceParameterMap, String uri, Charset charset) {
        LinkedMultiValueMap<String, String> parameterMap = RecyclableUtil.newLinkedMultiValueMap();
        try {
            decodeParams(parameterMap, uri, findPathEndIndex(uri), charset);
            for (Map.Entry<String, List<String>> entry : parameterMap.entrySet()) {
                String key = entry.getKey();
                List<String> newValueList = entry.getValue();
                String[] values = sourceParameterMap.get(key);
                if (values != null) {
                    for (String element : values) {
                        newValueList.add(element);
                    }
                }
                sourceParameterMap.put(key, newValueList.toArray(new String[newValueList.size()]));
            }
        } finally {
            parameterMap.clear();
        }
    }

    public static String decodeCharacterEncoding(String contentType) {
        if (contentType == null) {
            return null;
        }
        int start = contentType.indexOf(CHARSET_APPEND);
        if (start < 0) {
            return null;
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
                && (encoding.endsWith("\""))) {
            encoding = encoding.substring(1, encoding.length() - 1);
        }
        return encoding.trim();
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookieName  cookieName
     * @param cookieValue cookieValue
     * @param maxAge      maxAge
     * @param path        path
     * @param domain      domain
     * @param secure      secure
     * @param httpOnly    httpOnly
     * @return a single Set-Cookie header value
     */
    public static String encodeCookie(String cookieName, String cookieValue, int maxAge, String path, String domain, boolean secure, boolean httpOnly) {
        StringBuilder buf = RecyclableUtil.newStringBuilder();
        buf.append(cookieName);
        buf.append((char) HttpConstants.EQUALS);
        buf.append(cookieValue);
        buf.append((char) HttpConstants.SEMICOLON);
        buf.append((char) HttpConstants.SP);

        if (maxAge > 0) {
            buf.append(HttpHeaderConstants.MAX_AGE_1.toString());
            buf.append((char) HttpConstants.EQUALS);
            buf.append(maxAge);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
//            Date expires = new Date(cookie.maxAge() * 1000 + System.currentTimeMillis());
//            addUnquoted(buf, HttpHeaderConstants.EXPIRES.toString(), HttpHeaderDateFormat.get().format(expires));
        }
        if (path != null) {
            buf.append(HttpHeaderConstants.PATH.toString());
            buf.append((char) HttpConstants.EQUALS);
            buf.append(path);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (domain != null) {
            buf.append(HttpHeaderConstants.DOMAIN.toString());
            buf.append((char) HttpConstants.EQUALS);
            buf.append(domain);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (secure) {
            buf.append(HttpHeaderConstants.SECURE);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (httpOnly) {
            buf.append(HttpHeaderConstants.HTTPONLY);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 2);
        }
        return buf.toString();
    }

    /**
     * Decodes the specified Set-Cookie HTTP header value into a
     *
     * @param header header
     * @return the decoded {@link Cookie}
     */
    public static Cookie[] decodeCookie(String header) {
        final int headerLen = header.length();

        if (headerLen == 0) {
            return EMPTY_COOKIE;
        }

        RecyclableArrayList cookies = RecyclableUtil.newRecyclableList(2);
        try {
            int i = 0;

            boolean rfc2965Style = false;
            if (header.regionMatches(true, 0, "$Version", 0, 8)) {
                // RFC 2965 style cookie, move to after version value
                i = header.indexOf(';') + 1;
                rfc2965Style = true;
            }

            loop:
            for (; ; ) {

                // Skip spaces and separators.
                for (; ; ) {
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
                    keyValLoop:
                    for (; ; ) {

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
                                StringBuilder newValueBuf = RecyclableUtil.newStringBuilder();

                                final char q = c;
                                boolean hadBackslash = false;
                                i++;
                                for (; ; ) {
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
//                    try {
                    cookies.add(new Cookie(name, value));
//                    } catch (IllegalArgumentException e) {
//                        LoggerFactoryX.getLogger(ServletUtil.class).warn("discard cookie. cause = {}", e.toString());
//                    }
                }
            }
            return cookies.toArray(EMPTY_COOKIE);
        } finally {
            cookies.recycle();
        }
    }

    /**
     * unWrapper
     *
     * @param response response
     * @return ServletHttpServletResponse
     */
    public static ServletHttpServletResponse unWrapper(ServletResponse response) {
        if (response instanceof ServletResponseWrapper) {
            return unWrapper(((ServletResponseWrapper) response).getResponse());
        }
        if (response instanceof ServletHttpServletResponse) {
            return (ServletHttpServletResponse) response;
        }
        return null;
    }

    /**
     * unWrapper
     *
     * @param request request
     * @return ServletHttpServletRequest
     */
    public static ServletHttpServletRequest unWrapper(ServletRequest request) {
        if (request instanceof ServletRequestWrapper) {
            return unWrapper(((ServletRequestWrapper) request).getRequest());
        }
        if (request instanceof ServletHttpServletRequest) {
            return (ServletHttpServletRequest) request;
        }
        return null;
    }

    private static int findPathEndIndex(String uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == '?' || c == '#') {
                return i;
            }
        }
        return len;
    }

    private static void decodeParams(LinkedMultiValueMap<String, String> parameterMap, String uri, int from, Charset charset) {
        int len = uri.length();
        if (from >= len) {
            return;
        }
        if (uri.charAt(from) == '?') {
            from++;
        }
        int nameStart = from;
        int valueStart = -1;
        int i;
        loop:
        for (i = from; i < len; i++) {
            switch (uri.charAt(i)) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case '&':
                case ';':
                    if (nameStart < i) {
                        addParam(uri, nameStart, valueStart, i, parameterMap, charset);
                    }
                    nameStart = i + 1;
                    break;
                case '#':
                    break loop;
                default:
                    // continue
            }
        }
        if (nameStart < i) {
            addParam(uri, nameStart, valueStart, i, parameterMap, charset);
        }
    }

    private static void addParam(String s, int nameStart, int valueStart, int valueEnd,
                                 LinkedMultiValueMap<String, String> parameterMap, Charset charset) {
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }
        String name = decodeComponent(s, nameStart, valueStart - 1, charset);
        String value = decodeComponent(s, valueStart, valueEnd, charset);
        parameterMap.add(name, value);
    }

    public static String decodeComponent(String s, int from, int toExcluded, Charset charset) {
        int len = toExcluded - from;
        if (len <= 0) {
            return EMPTY_STRING;
        }
        int firstEscaped = -1;
        for (int i = from; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c == '%' || c == '+') {
                firstEscaped = i;
                break;
            }
        }
        if (firstEscaped == -1) {
            return s.substring(from, toExcluded);
        }

        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity = (toExcluded - firstEscaped) / 3;
        if (decodedCapacity == 0) {
            return s.substring(from, toExcluded);
        }
        byte[] buf = PlatformDependent.allocateUninitializedArray(decodedCapacity);
        int bufIdx;

        StringBuilder strBuf = new StringBuilder(len);
        strBuf.append(s, from, firstEscaped);

        if (HEX2B == null) {
            initHex2bIfHaveMemory();
        }

        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c != '%') {
                strBuf.append(c != '+' ? c : SPACE);
                continue;
            }

            bufIdx = 0;
            do {
                if (i + 3 > toExcluded) {
                    return s.substring(from, toExcluded);
//                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + s);
                }
                int hi;
                int lo;
                if (HEX2B == null) {
                    hi = decodeHexNibble(s.charAt(i + 1));
                    lo = decodeHexNibble(s.charAt(i + 2));
                } else {
                    hi = HEX2B[s.charAt(i + 1)];
                    lo = HEX2B[s.charAt(i + 2)];
                }
                if (hi != -1 && lo != -1) {
                    buf[bufIdx++] = (byte) ((hi << 4) + lo);
                } else {
                    return s.substring(from, toExcluded);
//                    throw new IllegalArgumentException(String.format("invalid hex byte '%s' at index %d of '%s'", s.subSequence(pos, pos + 2), pos, s));
                }

                i += 3;
            } while (i < toExcluded && s.charAt(i) == '%');
            i--;

            strBuf.append(new String(buf, 0, bufIdx, charset));
        }
        return strBuf.toString();
    }

    private static int decodeHexNibble(final char c) {
        // Character.digit() is not used here, as it addresses a larger
        // set of characters (both ASCII and full-width latin letters).
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 0xA);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 0xA);
        }
        return -1;
    }
}
