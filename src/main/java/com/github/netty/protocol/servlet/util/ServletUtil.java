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
import java.util.*;

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
    private static long lastTimestamp = System.currentTimeMillis();
    private static Date lastDate = new Date(lastTimestamp);
    private static String nowRFCTime = DateFormatter.format(lastDate);

    public static void main(String[] args) {
        Cookie[] cookies = decodeCookie("BIDUPSID=8102ACE79DAB387C5E44B3D7F3B295C9; PSTM=1649222481; BDUSS=FVeDc0ZGJBcGRGcEw1SkdsMlQzbnpOWFh4Y3RtdFkxS0pHRU9hbXp3ZWpGSHRpRUFBQUFBJCQAAAAAAAAAAAEAAADd5-Csd2FuZzg0MjE1NjcyNwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKOHU2Kjh1NiSV; BDUSS_BFESS=FVeDc0ZGJBcGRGcEw1SkdsMlQzbnpOWFh4Y3RtdFkxS0pHRU9hbXp3ZWpGSHRpRUFBQUFBJCQAAAAAAAAAAAEAAADd5-Csd2FuZzg0MjE1NjcyNwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKOHU2Kjh1NiSV; BAIDUID=FA6A4B43A27888C5A28DA5273C28858D:FG=1; BDSFRCVID=WPDOJeC62uE7F0TDiIGC25UBFg2dIoJTH6f3-MK4j1YF2xxe6BRuEG0P2f8g0KuM0UmuogKK0mOTHUkF_2uxOjjg8UtVJeC6EG0Ptf8g0f5; H_BDCLCKID_SF=tbCfVID-tDK3DJ5N5-r_bIC3bfT2K46JHD7yWCkafPbcOR5Jj65CWf4ghM6RBq0OQbutQUo-Lh-aeJ3n3MA--t4nQR3vaM3B2j4e0pF5bhRCsq0x05oWe-bQypoa2lRB5DOMahkMal7xOM5cQlPK5JkgMx6MqpQJQeQ-5KQN3KJmfbL9bT3tjjTyja0HJjLjJn3fL-085JTSDnTkMITjh6PrqfR9BtQmJJuJ5fnK0DQUO4JGLjKhKhDX5bKq2T0fQg-q0DO6MP5xShTKWh_hjPP9DqKj0x-jLg3hVn0MWKbYqIQPKtnJyUnQhtnnBpQW3H8HL4nv2JcJbM5m3x6qLTKkQN3T-PKO5bRh_CFbJK_hbD-4e5REKPF3beTa54cbb4o2WbCQJ4cP8pcNLTDKjnLBypLe0UTj2GPH_4J4-qbKsDokhlO1j4_ejp5gQq5t0KvKhJ67LPO-8p5jDh3v25ksD-RtWxT4QmTy0hvctn6cShnaMUjrDRLbXU6BK5vPbNcZ0l8K3l02V-bIe-t2b6QhDHAtq6-HtRFsL-35HJ6oHRT1bJOK-tFHqxnHK5na02c9aJ5nJDoVhx5XyP5UKfKDLHKtbpjH5mQXWxc8QpP-eCOLXxrs0nI_34Og553DfgjvKl0MLnnWbb0xynoDhnb03xnMBMPjamOnaU5o3fAKftnOM46JehL3346-35543bRTLnLy5KJtMDFRe5DBD5o-jHRaKI6BMJAq_DLKHJOoDDv50MOcy4LdjG5t06bltNnRBlTT0K_hSqoDbT5fyt4p3-AqKjtLBm0JBDjw3DoWJbKCLl5hQfbQ0a5hqP-jW5TuoU5EbR7JOpkxhfnxyhLfQRPH-Rv92DQMVU52QqcqEIQHQT3m5-5bbN3ht6T2-DA__K-2tfK; BDSFRCVID_BFESS=WPDOJeC62uE7F0TDiIGC25UBFg2dIoJTH6f3-MK4j1YF2xxe6BRuEG0P2f8g0KuM0UmuogKK0mOTHUkF_2uxOjjg8UtVJeC6EG0Ptf8g0f5; H_BDCLCKID_SF_BFESS=tbCfVID-tDK3DJ5N5-r_bIC3bfT2K46JHD7yWCkafPbcOR5Jj65CWf4ghM6RBq0OQbutQUo-Lh-aeJ3n3MA--t4nQR3vaM3B2j4e0pF5bhRCsq0x05oWe-bQypoa2lRB5DOMahkMal7xOM5cQlPK5JkgMx6MqpQJQeQ-5KQN3KJmfbL9bT3tjjTyja0HJjLjJn3fL-085JTSDnTkMITjh6PrqfR9BtQmJJuJ5fnK0DQUO4JGLjKhKhDX5bKq2T0fQg-q0DO6MP5xShTKWh_hjPP9DqKj0x-jLg3hVn0MWKbYqIQPKtnJyUnQhtnnBpQW3H8HL4nv2JcJbM5m3x6qLTKkQN3T-PKO5bRh_CFbJK_hbD-4e5REKPF3beTa54cbb4o2WbCQJ4cP8pcNLTDKjnLBypLe0UTj2GPH_4J4-qbKsDokhlO1j4_ejp5gQq5t0KvKhJ67LPO-8p5jDh3v25ksD-RtWxT4QmTy0hvctn6cShnaMUjrDRLbXU6BK5vPbNcZ0l8K3l02V-bIe-t2b6QhDHAtq6-HtRFsL-35HJ6oHRT1bJOK-tFHqxnHK5na02c9aJ5nJDoVhx5XyP5UKfKDLHKtbpjH5mQXWxc8QpP-eCOLXxrs0nI_34Og553DfgjvKl0MLnnWbb0xynoDhnb03xnMBMPjamOnaU5o3fAKftnOM46JehL3346-35543bRTLnLy5KJtMDFRe5DBD5o-jHRaKI6BMJAq_DLKHJOoDDv50MOcy4LdjG5t06bltNnRBlTT0K_hSqoDbT5fyt4p3-AqKjtLBm0JBDjw3DoWJbKCLl5hQfbQ0a5hqP-jW5TuoU5EbR7JOpkxhfnxyhLfQRPH-Rv92DQMVU52QqcqEIQHQT3m5-5bbN3ht6T2-DA__K-2tfK; MCITY=-%3A; BDORZ=B490B5EBF6F3CD402E515D22BCDA1598; ZFY=tJm0FxyxWMOdcxePxlSKr:BAn1tNBbLH1iCVEx4a4qlw:C; BAIDUID_BFESS=FA6A4B43A27888C5A28DA5273C28858D:FG=1; delPer=0; PSINO=1; H_PS_PSSID=36545_36625_36642_36255_36722_36413_36955_36948_36167_36917_36966_36745_26350; BA_HECTOR=ag2lal208g0g258l052klhuv1hefqh916");
        LinkedMultiValueMap<String,String> map = new LinkedMultiValueMap<>();
        decodeByUrl(map,"/s?ie=utf-8&f=8&rsv_bp=1&tn=baidu&wd=65536%2F1024&oq=0x20&rsv_pq=a691c8cc00056d8e&rsv_t=7d67rqRGLzSLre32A0lc%2BfclrEw3Mq%2BNaDXCRxDam9XndYsYBig5AE0vWmg&rqlang=cn&rsv_enter=1&rsv_dl=tb&rsv_sug3=8&rsv_sug1=10&rsv_sug7=100&rsv_n=2&rsv_sug2=0&rsv_btype=t&inputT=3012&rsv_sug4=1100455",Charset.forName("utf-8"));

        System.out.println("cookies = " + cookies);
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

    private static String decodeComponent(String s, int from, int toExcluded, Charset charset) {
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
        byte[] buf = PlatformDependent.allocateUninitializedArray(decodedCapacity);
        int bufIdx;

        StringBuilder strBuf = new StringBuilder(len);
        strBuf.append(s, from, firstEscaped);

        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c != '%') {
                strBuf.append(c != '+' ? c : SPACE);
                continue;
            }

            bufIdx = 0;
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + s);
                }
                buf[bufIdx++] = decodeHexByte(s, i + 1);
                i += 3;
            } while (i < toExcluded && s.charAt(i) == '%');
            i--;

            strBuf.append(new String(buf, 0, bufIdx, charset));
        }
        return strBuf.toString();
    }

    private static byte decodeHexByte(CharSequence s, int pos) {
        int hi = HEX2B[s.charAt(pos)];
        int lo = HEX2B[s.charAt(pos + 1)];
        if (hi != -1 && lo != -1) {
            return (byte) ((hi << 4) + lo);
        } else {
            throw new IllegalArgumentException(String.format("invalid hex byte '%s' at index %d of '%s'", s.subSequence(pos, pos + 2), pos, s));
        }
    }

    private static final byte[] HEX2B;

    static {
        HEX2B = new byte[65536];
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
}
