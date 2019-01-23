package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.CookieCodecUtil;
import com.github.netty.protocol.servlet.NettyHttpCookie;
import com.github.netty.protocol.servlet.ServletHttpServletRequest;
import com.github.netty.protocol.servlet.ServletHttpServletResponse;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocal;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 工具类
 * @author acer01
 *  2018/7/15/015
 */
public class ServletUtil {

    /**
     * 生成HTTP报头时允许的唯一日期格式。
     */
    private static final String RFC1123_DATE =
            "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final SimpleDateFormat[] FORMATS_TEMPLATE = {
            new SimpleDateFormat(RFC1123_DATE, Locale.ENGLISH),
            new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.ENGLISH),
            new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.ENGLISH)
    };
    private static final String EMPTY_STRING = "";
    private static final char SPACE = 0x20;
    /**
     * SimpleDateFormat非线程安全，为了节省内存提高效率，把他放在ThreadLocal里
     * 用于设置HTTP响应头的时间信息
     */
    private static final FastThreadLocal<DateFormat> HTTP_DATE_FORMAT = new FastThreadLocal<DateFormat>() {
        private TimeZone timeZone = TimeZone.getTimeZone("GMT");
        @Override
        protected DateFormat initialValue() {
            DateFormat df = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.ENGLISH);
            df.setTimeZone(timeZone);
            return df;
        }
    };

    public static String getCookieValue(Cookie[] cookies, String cookieName){
        if(cookies == null || cookieName == null) {
            return null;
        }
        for(Cookie cookie : cookies){
            if(cookie == null) {
                continue;
            }

            String name = cookie.getName();
            if(cookieName.equals(name)){
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void decodeByUrl(Map<String,String[]> parameterMap, String uri, Charset charset){
        Map<String, List<String>> parameterListMap = decodeParams(uri, findPathEndIndex(uri), charset, 10000);
        for(Map.Entry<String,List<String>> entry : parameterListMap.entrySet()){
            List<String> value = entry.getValue();
            parameterMap.put(entry.getKey(), value.toArray(new String[value.size()]));
        }
    }

    public static String decodeCharacterEncoding(String contentType) {
        if (contentType == null) {
            return null;
        }
        int start = contentType.indexOf(HttpHeaderConstants.CHARSET + "=");
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

    public static String encodeCookie(io.netty.handler.codec.http.Cookie cookie){
        String value = CookieCodecUtil.encode(cookie);
        return value;
    }

    public static Cookie[] decodeCookie(String value){
        if(value == null){
            return null;
        }

        Collection<io.netty.handler.codec.http.Cookie> nettyCookieSet = CookieCodecUtil.decode(value);
        if(nettyCookieSet == null){
            return null;
        }

        io.netty.handler.codec.http.Cookie[] nettyCookieArr = nettyCookieSet.toArray(new io.netty.handler.codec.http.Cookie[nettyCookieSet.size()]);
        int size = nettyCookieArr.length;
        Cookie[] cookies = new Cookie[size];

        NettyHttpCookie nettyHttpCookie = new NettyHttpCookie();
        for (int i=0; i< size; i++) {
            io.netty.handler.codec.http.Cookie nettyCookie = nettyCookieArr[i];
            if(nettyCookie == null){
                continue;
            }

            nettyHttpCookie.wrap(nettyCookie);
            cookies[i] = toServletCookie(nettyHttpCookie);
        }
        return cookies;
    }

    public static Cookie toServletCookie(NettyHttpCookie nettyCookie){
        Cookie cookie = new Cookie(nettyCookie.getName(), nettyCookie.getValue());
        String comment = nettyCookie.getComment();
        if(comment != null) {
            cookie.setComment(comment);
        }
        String domain = nettyCookie.getDomain();
        if(domain != null) {
            cookie.setDomain(domain);
        }
        cookie.setHttpOnly(nettyCookie.isHttpOnly());
        cookie.setMaxAge((int) nettyCookie.getMaxAge());
        cookie.setPath(nettyCookie.getPath());
        cookie.setVersion(nettyCookie.getVersion());
        cookie.setSecure(nettyCookie.isSecure());
        return cookie;
    }

    public static io.netty.handler.codec.http.Cookie toNettyCookie(Cookie cookie){
        io.netty.handler.codec.http.Cookie nettyCookie = new DefaultCookie(cookie.getName(),cookie.getValue());
        String comment = cookie.getComment();
        if(comment != null) {
            nettyCookie.setComment(comment);
        }
        String domain = cookie.getDomain();
        if(domain != null) {
            nettyCookie.setDomain(domain);
        }
        nettyCookie.setHttpOnly(cookie.isHttpOnly());
        nettyCookie.setMaxAge(cookie.getMaxAge());
        nettyCookie.setPath(cookie.getPath());
        nettyCookie.setVersion(cookie.getVersion());
        nettyCookie.setSecure(cookie.getSecure());

        return nettyCookie;
    }

    public static Long parseHeaderDate(String value) {
        DateFormat[] formats = FORMATS_TEMPLATE;
        Date date = null;
        for (int i = 0; (date == null) && (i < formats.length); i++) {
            try {
                date = formats[i].parse(value);
            } catch (ParseException e) {
                // Ignore
            }
        }
        if (date == null) {
            return null;
        }
        return date.getTime();
    }


    /**
     * @return 线程安全的获取当前时间格式化后的字符串
     */
    public static String newDateGMT() {
        return HTTP_DATE_FORMAT.get().format(new Date());
    }

    /**
     * 去掉包装
     * @param response
     * @return
     */
    public static ServletHttpServletResponse unWrapper(ServletResponse response){
        if(response instanceof ServletResponseWrapper){
            return unWrapper(((ServletResponseWrapper) response).getResponse());
        }
        if(response instanceof ServletHttpServletResponse){
            return (ServletHttpServletResponse) response;
        }
        return null;
    }

    /**
     * 去掉包装
     * @param request
     * @return
     */
    public static ServletHttpServletRequest unWrapper(ServletRequest request){
        if(request instanceof ServletRequestWrapper){
            return unWrapper(((ServletRequestWrapper) request).getRequest());
        }
        if(request instanceof ServletHttpServletRequest){
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

    private static Map<String, List<String>> decodeParams(String s, int from, Charset charset, int paramsLimit) {
        int len = s.length();
        if (from >= len) {
            return Collections.emptyMap();
        }
        if (s.charAt(from) == '?') {
            from++;
        }
        Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        loop:
        for (i = from; i < len; i++) {
            switch (s.charAt(i)) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case '&':
                case ';':
                    if (addParam(s, nameStart, valueStart, i, params, charset)) {
                        paramsLimit--;
                        if (paramsLimit == 0) {
                            return params;
                        }
                    }
                    nameStart = i + 1;
                    break;
                case '#':
                    break loop;
                default:
                    // continue
            }
        }
        addParam(s, nameStart, valueStart, i, params, charset);
        return params;
    }

    private static boolean addParam(String s, int nameStart, int valueStart, int valueEnd,
                                    Map<String, List<String>> params, Charset charset) {
        if (nameStart >= valueEnd) {
            return false;
        }
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }
        String name = decodeComponent(s, nameStart, valueStart - 1, charset);
        String value = decodeComponent(s, valueStart, valueEnd, charset);
        List<String> values = params.get(name);
        if (values == null) {
            values = new ArrayList<String>(1);  // Often there's only 1 value.
            params.put(name, values);
        }
        values.add(value);
        return true;
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

        CharsetDecoder decoder = CharsetUtil.decoder(charset);

        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity = (toExcluded - firstEscaped) / 3;
        ByteBuffer byteBuf = ByteBuffer.allocate(decodedCapacity);
        CharBuffer charBuf = CharBuffer.allocate(decodedCapacity);

        StringBuilder strBuf = new StringBuilder(len);
        strBuf.append(s, from, firstEscaped);

        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c != '%') {
                strBuf.append(c != '+' ? c : SPACE);
                continue;
            }

            byteBuf.clear();
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + s);
                }

                byteBuf.put(decodeHexByte(s, i + 1));
                i += 3;
            } while (i < toExcluded && s.charAt(i) == '%');
            i--;

            byteBuf.flip();
            charBuf.clear();
            CoderResult result = decoder.reset().decode(byteBuf, charBuf, true);
            try {
                if (!result.isUnderflow()) {
                    result.throwException();
                }
                result = decoder.flush(charBuf);
                if (!result.isUnderflow()) {
                    result.throwException();
                }
            } catch (CharacterCodingException ex) {
                throw new IllegalStateException(ex);
            }
            strBuf.append(charBuf.flip());
        }
        return strBuf.toString();
    }

    private static byte decodeHexByte(CharSequence s, int pos) {
        int hi = decodeHexNibble(s.charAt(pos));
        int lo = decodeHexNibble(s.charAt(pos + 1));
        if (hi == -1 || lo == -1) {
            throw new IllegalArgumentException(String.format(
                    "invalid hex byte '%s' at index %d of '%s'", s.subSequence(pos, pos + 2), pos, s));
        }
        return (byte) ((hi << 4) + lo);
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

    private static final String SERVER_INFO;
    private static final String SERVER_BUILT;
    private static final String SERVER_NUMBER;
    private static final String JVM_VERSION;
    private static final String ARCH;
    private static final String OS_NAME;

    static {
        String info = null;
        String built = null;
        String number = null;

        Properties props = new Properties();
        try (InputStream is = ServletUtil.class.getResourceAsStream
                ("/server.properties")) {
            props.load(is);
            info = props.getProperty("server.info");
            built = props.getProperty("server.built");
            number = props.getProperty("server.number");
        } catch (Throwable t) {
            //
        }
        if (info == null) {
            info = "Github NettyX/2.0.x-dev";
        }
        if (built == null) {
            built = "unknown";
        }
        if (number == null) {
            number = "2.0.x";
        }
        SERVER_INFO = info;
        SERVER_BUILT = built;
        SERVER_NUMBER = number;
        OS_NAME = System.getProperty("os.name");
        ARCH = System.getProperty("os.arch");
        JVM_VERSION = System.getProperty("java.runtime.version");
    }

    public static String getServerInfo() {
        return SERVER_INFO;
    }

    public static String getServerBuilt() {
        return SERVER_BUILT;
    }

    public static String getServerNumber() {
        return SERVER_NUMBER;
    }

    public static String getJvmVersion() {
        return JVM_VERSION;
    }

    public static String getArch() {
        return ARCH;
    }

    public static String getOsName() {
        return OS_NAME;
    }

    public static void main(String args[]) {
        System.out.println("Server version: " + getServerInfo());
        System.out.println("Server built:   " + getServerBuilt());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " +
                getOsName());
        System.out.println("OS Version:     " +
                System.getProperty("os.version"));
        System.out.println("Architecture:   " +
                getArch());
        System.out.println("JVM Version:    " +
                getJvmVersion());
        System.out.println("JVM Vendor:     " +
                System.getProperty("java.vm.vendor"));
    }
}
