package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.LinkedMultiValueMap;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.servlet.ServletHttpServletRequest;
import com.github.netty.protocol.servlet.ServletHttpServletResponse;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.AsciiString;
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
    private static final String CHARSET_APPEND = HttpHeaderConstants.CHARSET + "=";
    private static byte[] HEX2B;
    private static long lastTimestamp = System.currentTimeMillis();
    private static final Date lastDate = new Date(lastTimestamp);
    private static CharSequence nowRFCTime = DateFormatter.format(lastDate);

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

    public static CharSequence getDateByRfcHttp() {
        long timestamp = System.currentTimeMillis();
        //cache 1/s
        if (timestamp - lastTimestamp > 1000L) {
            lastTimestamp = timestamp;
            lastDate.setTime(timestamp);
            nowRFCTime = new AsciiString(DateFormatter.format(lastDate));
        }
        return nowRFCTime;
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
    public static CharSequence encodeCookie(StringBuilder buf, String cookieName, String cookieValue, int maxAge, CharSequence path, String domain, boolean secure, boolean httpOnly) {
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
        return new AsciiString(buf);
    }

    /**
     * Decodes the specified Set-Cookie HTTP header value into a
     *
     * @param header header
     * @return the decoded {@link Cookie}
     */
    public static Cookie[] decodeCookie(String header) {
        final int headerLen = header.length();

        StringBuilder newValueBuf = null;
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
                                if (newValueBuf == null) {
                                    newValueBuf = RecyclableUtil.newStringBuilder();
                                } else {
                                    newValueBuf.setLength(0);
                                }

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
            return cookies.toArray(new Cookie[cookies.size()]);
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
            return "";
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
                strBuf.append(c != '+' ? c : ' ');
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

    /**
     * Return <code>true</code> if the context-relative request path matches the requirements of the specified filter
     * mapping; otherwise, return <code>false</code>.
     *
     * @param testPath                   URL mapping being checked
     * @param contextRelativeRequestPath Context-relative request path of this request
     */
    public static boolean matchFiltersURL(String testPath, String contextRelativeRequestPath) {
        // Case 1 - Exact Match
        if (testPath.equals(contextRelativeRequestPath)) {
            return true;
        }

        // Case 2 - Path Match ("/.../*")
        if ("/*".equals(testPath)) {
            return true;
        }
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, contextRelativeRequestPath, 0, testPath.length() - 2)) {
                if (contextRelativeRequestPath.length() == (testPath.length() - 2)) {
                    return true;
                } else if ('/' == contextRelativeRequestPath.charAt(testPath.length() - 2)) {
                    return true;
                }
            }
            return false;
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = contextRelativeRequestPath.lastIndexOf('/');
            int period = contextRelativeRequestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) && (period != contextRelativeRequestPath.length() - 1) &&
                    ((contextRelativeRequestPath.length() - period) == (testPath.length() - 1))) {
                return testPath.regionMatches(2, contextRelativeRequestPath, period + 1, testPath.length() - 2);
            }
        }

        // Case 4 - "Default" Match
        return false; // NOTE - Not relevant for selecting filters
    }

    public static String normPrefixPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        if (path.length() > 1 && !path.startsWith("/") && !path.startsWith("*")) {
            path = "/" + path;
        }
        return path;
    }

    public static String normSuffixPath(String path) {
        while (path != null && path.endsWith("//")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }


    /**
     * Determine if the character is allowed in the scheme of a URI. See RFC 2396, Section 3.1
     *
     * @param c The character to test
     * @return {@code true} if a the character is allowed, otherwise {@code
     * false}
     */
    private static boolean isSchemeChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
    }


    /**
     * Determine if a URI string has a <code>scheme</code> component.
     *
     * @param uri The URI to test
     * @return {@code true} if a scheme is present, otherwise {code @false}
     */
    public static boolean hasScheme(CharSequence uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == ':') {
                return i > 0;
            } else if (!isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }

    public static CharSequence uriNormalize(CharSequence ps) {
        // Does this path need normalization?
        int pathLen = ps.length();
        int ns = needsNormalization(ps, pathLen);        // Number of segments
        if (ns < 0)
            // Nope -- just return it
            return ps;

        char[] path = new char[pathLen];         // Path in char-array form
        if (ps instanceof String) {
            ((String) ps).getChars(0, pathLen, path, 0);
        } else if (ps instanceof StringBuilder) {
            ((StringBuilder) ps).getChars(0, pathLen, path, 0);
        } else if (ps instanceof StringBuffer) {
            ((StringBuffer) ps).getChars(0, pathLen, path, 0);
        } else {
            ps.toString().getChars(0, pathLen, path, 0);
        }

        // Split path into segments
        int[] segs = new int[ns];               // Segment-index array
        split(path, segs);

        // Remove dots
        removeDots(path, segs);

        // Prevent scheme-name confusion
        maybeAddLeadingDot(path, segs);

        // Join the remaining segments and return the result
        String s = new String(path, 0, join(path, segs));
        if (s.contentEquals(ps)) {
            // string was already normalized
            return ps;
        }
        return s;
    }

    // Join the segments in the given path according to the given segment-index
    // array, ignoring those segments whose index entries have been set to -1,
    // and inserting slashes as needed.  Return the length of the resulting
    // path.
    //
    // Preconditions:
    //   segs[i] == -1 implies segment i is to be ignored
    //   path computed by split, as above, with '\0' having replaced '/'
    //
    // Postconditions:
    //   path[0] .. path[return value] == Resulting path
    //
    private static int join(char[] path, int[] segs) {
        int ns = segs.length;           // Number of segments
        int end = path.length - 1;      // Index of last char in path
        int p = 0;                      // Index of next path char to write

        if (path[p] == '\0') {
            // Restore initial slash for absolute paths
            path[p++] = '/';
        }

        for (int i = 0; i < ns; i++) {
            int q = segs[i];            // Current segment
            if (q == -1)
                // Ignore this segment
                continue;

            if (p == q) {
                // We're already at this segment, so just skip to its end
                while ((p <= end) && (path[p] != '\0'))
                    p++;
                if (p <= end) {
                    // Preserve trailing slash
                    path[p++] = '/';
                }
            } else if (p < q) {
                // Copy q down to p
                while ((q <= end) && (path[q] != '\0'))
                    path[p++] = path[q++];
                if (q <= end) {
                    // Preserve trailing slash
                    path[p++] = '/';
                }
            }
        }

        return p;
    }

    // DEVIATION: If the normalized path is relative, and if the first
    // segment could be parsed as a scheme name, then prepend a "." segment
    //
    private static void maybeAddLeadingDot(char[] path, int[] segs) {

        if (path[0] == '\0')
            // The path is absolute
            return;

        int ns = segs.length;
        int f = 0;                      // Index of first segment
        while (f < ns) {
            if (segs[f] >= 0)
                break;
            f++;
        }
        if ((f >= ns) || (f == 0))
            // The path is empty, or else the original first segment survived,
            // in which case we already know that no leading "." is needed
            return;

        int p = segs[f];
        while ((p < path.length) && (path[p] != ':') && (path[p] != '\0')) p++;
        if (p >= path.length || path[p] == '\0')
            // No colon in first segment, so no "." needed
            return;

        // At this point we know that the first segment is unused,
        // hence we can insert a "." segment at that position
        path[0] = '.';
        path[1] = '\0';
        segs[0] = 0;
    }


    // Remove "." segments from the given path, and remove segment pairs
    // consisting of a non-".." segment followed by a ".." segment.
    //
    private static void removeDots(char[] path, int[] segs) {
        int ns = segs.length;
        int end = path.length - 1;

        for (int i = 0; i < ns; i++) {
            int dots = 0;               // Number of dots found (0, 1, or 2)

            // Find next occurrence of "." or ".."
            do {
                int p = segs[i];
                if (path[p] == '.') {
                    if (p == end) {
                        dots = 1;
                        break;
                    } else if (path[p + 1] == '\0') {
                        dots = 1;
                        break;
                    } else if ((path[p + 1] == '.')
                            && ((p + 1 == end)
                            || (path[p + 2] == '\0'))) {
                        dots = 2;
                        break;
                    }
                }
                i++;
            } while (i < ns);
            if ((i > ns) || (dots == 0))
                break;

            if (dots == 1) {
                // Remove this occurrence of "."
                segs[i] = -1;
            } else {
                // If there is a preceding non-".." segment, remove both that
                // segment and this occurrence of ".."; otherwise, leave this
                // ".." segment as-is.
                int j;
                for (j = i - 1; j >= 0; j--) {
                    if (segs[j] != -1) break;
                }
                if (j >= 0) {
                    int q = segs[j];
                    if (!((path[q] == '.')
                            && (path[q + 1] == '.')
                            && (path[q + 2] == '\0'))) {
                        segs[i] = -1;
                        segs[j] = -1;
                    }
                }
            }
        }
    }

    // Split the given path into segments, replacing slashes with nulls and
    // filling in the given segment-index array.
    //
    // Preconditions:
    //   segs.length == Number of segments in path
    //
    // Postconditions:
    //   All slashes in path replaced by '\0'
    //   segs[i] == Index of first char in segment i (0 <= i < segs.length)
    //
    private static void split(char[] path, int[] segs) {
        int end = path.length - 1;      // Index of last char in path
        int p = 0;                      // Index of next char in path
        int i = 0;                      // Index of current segment

        // Skip initial slashes
        while (p <= end) {
            if (path[p] != '/') break;
            path[p] = '\0';
            p++;
        }

        while (p <= end) {

            // Note start of segment
            segs[i++] = p++;

            // Find beginning of next segment
            while (p <= end) {
                if (path[p++] != '/')
                    continue;
                path[p - 1] = '\0';

                // Skip redundant slashes
                while (p <= end) {
                    if (path[p] != '/') break;
                    path[p++] = '\0';
                }
                break;
            }
        }
    }

    // Check the given path to see if it might need normalization.  A path
    // might need normalization if it contains duplicate slashes, a "."
    // segment, or a ".." segment.  Return -1 if no further normalization is
    // possible, otherwise return the number of segments found.
    //
    // This method takes a string argument rather than a char array so that
    // this test can be performed without invoking path.toCharArray().
    //
    private static int needsNormalization(CharSequence path, int pathLen) {
        boolean normal = true;
        int ns = 0;                     // Number of segments
        int end = pathLen - 1;    // Index of last char in path
        int p = 0;                      // Index of next char in path

        // Skip initial slashes
        while (p <= end) {
            if (path.charAt(p) != '/') break;
            p++;
        }
        if (p > 1) normal = false;

        // Scan segments
        while (p <= end) {

            // Looking at "." or ".." ?
            if ((path.charAt(p) == '.')
                    && ((p == end)
                    || ((path.charAt(p + 1) == '/')
                    || ((path.charAt(p + 1) == '.')
                    && ((p + 1 == end)
                    || (path.charAt(p + 2) == '/')))))) {
                normal = false;
            }
            ns++;

            // Find beginning of next segment
            while (p <= end) {
                if (path.charAt(p++) != '/')
                    continue;

                // Skip redundant slashes
                while (p <= end) {
                    if (path.charAt(p) != '/') break;
                    normal = false;
                    p++;
                }

                break;
            }
        }

        return normal ? -1 : ns;
    }

    /**
     * Normalize a relative URI path that may have relative values ("/./", "/../", and so on ) it it.
     * <strong>WARNING</strong> - This method is useful only for normalizing application-generated paths. It does not
     * try to perform security checks for malicious input.
     *
     * @param path             Relative path to be normalized
     * @param replaceBackSlash Should '\\' be replaced with '/'
     * @return The normalized path or <code>null</code> if the path cannot be normalized
     */
    public static String pathNormalize(String path, boolean replaceBackSlash) {
        if (path == null) {
            return null;
        }
        // Create a place for the normalized path
        String normalized = path;

        if (replaceBackSlash && normalized.indexOf('\\') >= 0) {
            normalized = normalized.replace('\\', '/');
        }

        // Add a leading "/" if necessary
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        boolean addedTrailingSlash = false;
        if (normalized.endsWith("/.") || normalized.endsWith("/..")) {
            normalized = normalized + "/";
            addedTrailingSlash = true;
        }

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0) {
                break;
            }
            if (index == 0) {
                return null; // Trying to go outside our context
            }
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
        }

        if (normalized.length() > 1 && addedTrailingSlash) {
            // Remove the trailing '/' we added to that input and output are
            // consistent w.r.t. to the presence of the trailing '/'.
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Return the normalized path that we have completed
        return normalized;
    }

}
