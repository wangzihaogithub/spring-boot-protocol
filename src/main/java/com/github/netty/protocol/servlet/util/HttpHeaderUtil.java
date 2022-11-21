package com.github.netty.protocol.servlet.util;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author wangzihao
 */
public class HttpHeaderUtil {
    private static final int ARRAY_SIZE = 128;
    private static final boolean[] IS_TOKEN = new boolean[ARRAY_SIZE];
    private static final String CONTENT_TYPE_URLENCODED = "application/x-www-form-urlencoded";

    static {
        boolean[] IS_CONTROL = new boolean[ARRAY_SIZE];
        boolean[] IS_SEPARATOR = new boolean[ARRAY_SIZE];
        for (int i = 0; i < ARRAY_SIZE; i++) {
            // Control> 0-31, 127
            if (i < 32 || i == 127) {
                IS_CONTROL[i] = true;
            }
            // Separator
            if (i == '(' || i == ')' || i == '<' || i == '>' || i == '@' ||
                    i == ',' || i == ';' || i == ':' || i == '\\' || i == '\"' ||
                    i == '/' || i == '[' || i == ']' || i == '?' || i == '=' ||
                    i == '{' || i == '}' || i == ' ' || i == '\t') {
                IS_SEPARATOR[i] = true;
            }
            // Token: Anything 0-127 that is not a control and not a separator
            if (!IS_CONTROL[i] && !IS_SEPARATOR[i]) {
                IS_TOKEN[i] = true;
            }
        }
    }

    public static List<String> splitProtocolsHeader(CharSequence header) {
        StringBuilder builder = new StringBuilder(header.length());
        List<String> protocols = new ArrayList<>(2);
        for (int i = 0; i < header.length(); ++i) {
            char c = header.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == ',') {
                protocols.add(builder.toString());
                builder.setLength(0);
            } else {
                builder.append((char) Character.toLowerCase((int) c & 0xff));
            }
        }
        if (builder.length() > 0) {
            protocols.add(builder.toString());
        }
        return protocols;
    }

    public static boolean isFormUrlEncoder(String contentType) {
        if (contentType == null || contentType.length() < CONTENT_TYPE_URLENCODED.length()) {
            return false;
        }
        for (int i = 0, len = CONTENT_TYPE_URLENCODED.length(); i < len; i++) {
            char c1 = contentType.charAt(i);
            char c2 = CONTENT_TYPE_URLENCODED.charAt(i);
            if (c1 == c2) {
                continue;
            }
            if (Character.toLowerCase(c1) == Character.toLowerCase(c2)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * 移除头部不支持拖挂的字段
     *
     * @param lastHttpContent 最后一次内容
     */
    public static void removeHeaderUnSupportTrailer(LastHttpContent lastHttpContent) {
        if (lastHttpContent == null) {
            return;
        }
        if (lastHttpContent == LastHttpContent.EMPTY_LAST_CONTENT) {
            return;
        }
        HttpHeaders headers = lastHttpContent.trailingHeaders();
        if (headers.isEmpty()) {
            return;
        }
        headers.remove(HttpHeaderConstants.TRANSFER_ENCODING.toString());
        headers.remove(HttpHeaderConstants.CONTENT_LENGTH.toString());
        headers.remove(HttpHeaderConstants.TRAILER.toString());
    }

    /**
     * 是否接受分段传输
     *
     * @param headers headers
     * @return boolean
     */
    public static boolean isAcceptTransferChunked(HttpHeaders headers) {
        String transferEncodingValue = headers.get(HttpHeaderConstants.TE);
        if (transferEncodingValue == null || transferEncodingValue.isEmpty()) {
            return true;
        }
        return headers.contains(HttpHeaderConstants.TE, HttpHeaderConstants.CHUNKED.toString(), true);
    }

    /**
     * Returns {@code true} if and only if the connection can remain open and
     * thus 'kept alive'.  This methods respects the value of the
     * {@code "Connection"} header first and then the return value of
     * {@link HttpVersion#isKeepAliveDefault()}.
     *
     * @param message message
     * @return boolean isKeepAlive
     */
    public static boolean isKeepAlive(HttpRequest message) {
        HttpHeaders headers = message.headers();

        Object connectionValueObj = headers.get(HttpHeaderConstants.CONNECTION);

        //如果协议支持
        if (message.getProtocolVersion().isKeepAliveDefault()) {
            //不包含close就是保持
            return connectionValueObj == null || "".equals(connectionValueObj) ||
                    !HttpHeaderConstants.CLOSE.toString().equalsIgnoreCase(connectionValueObj.toString());
        } else {
            //如果协议不支持, 有keep-alive就是保持
            return connectionValueObj != null &&
                    HttpHeaderConstants.KEEP_ALIVE.toString().equalsIgnoreCase(connectionValueObj.toString());
        }
    }

    /**
     * Sets the value of the {@code "Connection"} header depending on the
     * protocol version of the specified message.  This getMethod sets or removes
     * the {@code "Connection"} header depending on what the default keep alive
     * mode of the message's protocol version is, as specified by
     * {@link HttpVersion#isKeepAliveDefault()}.
     * <ul>
     * <li>If the connection is kept alive by default:
     * <ul>
     * <li>set to {@code "close"} if {@code keepAlive} is {@code false}.</li>
     * <li>remove otherwise.</li>
     * </ul></li>
     * <li>If the connection is closed by default:
     * <ul>
     * <li>set to {@code "keep-alive"} if {@code keepAlive} is {@code true}.</li>
     * <li>remove otherwise.</li>
     * </ul></li>
     * </ul>
     *
     * @param message   message
     * @param keepAlive keepAlive
     */
    public static void setKeepAlive(HttpResponse message, boolean keepAlive) {
        HttpHeaders h = message.headers();
        if (message.protocolVersion().isKeepAliveDefault()) {
            if (keepAlive) {
                h.remove(HttpHeaderConstants.CONNECTION);
            } else {
                h.set(HttpHeaderConstants.CONNECTION, HttpHeaderConstants.CLOSE);
            }
        } else {
            if (keepAlive) {
                h.set(HttpHeaderConstants.CONNECTION, HttpHeaderConstants.KEEP_ALIVE);
            } else {
                h.remove(HttpHeaderConstants.CONNECTION);
            }
        }
    }

    public static boolean isUnsupportedExpectation(HttpMessage message) {
        if (!isExpectHeaderValid(message)) {
            return false;
        }

        final String expectValue = message.headers().get(HttpHeaderNames.EXPECT);
        return expectValue != null && !HttpHeaderValues.CONTINUE.toString().equalsIgnoreCase(expectValue);
    }

    private static boolean isExpectHeaderValid(final HttpMessage message) {
        /*
         * Expect: 100-continue is for requests only and it works only on HTTP/1.1 or later. Note further that RFC 7231
         * section 5.1.1 says "A server that receives a 100-continue expectation in an HTTP/1.0 request MUST ignore
         * that expectation."
         */
        return message instanceof HttpRequest &&
                message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) >= 0;
    }

    /**
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @param message      message
     * @param defaultValue defaultValue
     * @return the content length or {@code defaultValue} if this message does
     * not have the {@code "Content-Length"} header or its value is not
     * a number
     */
    public static long getContentLength(HttpMessage message, long defaultValue) {
        String str = message.headers().get(HttpHeaderConstants.CONTENT_LENGTH);
        if (str != null && str.length() > 0) {
            return Long.parseLong(str);
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength(message);
        if (webSocketContentLength != -1) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        return defaultValue;
    }

    /**
     * Returns the content length of the specified web socket message.  If the
     * specified message is not a web socket message, {@code -1} is returned.
     */
    private static int getWebSocketContentLength(HttpMessage message) {
        // WebSockset messages have constant content-lengths.
        HttpHeaders h = message.headers();
        if (message instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) message;
            if (HttpMethod.GET.equals(req.method()) &&
                    h.contains(HttpHeaderConstants.SEC_WEBSOCKET_KEY1) &&
                    h.contains(HttpHeaderConstants.SEC_WEBSOCKET_KEY2)) {
                return 8;
            }
        } else if (message instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) message;
            if (res.status().code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code() &&
                    h.contains(HttpHeaderConstants.SEC_WEBSOCKET_ORIGIN) &&
                    h.contains(HttpHeaderConstants.SEC_WEBSOCKET_LOCATION)) {
                return 16;
            }
        }


        // Not a web socket message
        return -1;
    }

    /**
     * Sets the {@code "Content-Length"} header.
     *
     * @param headers headers
     * @param length  length
     */
    public static void setContentLength(HttpHeaders headers, long length) {
        headers.set(HttpHeaderConstants.CONTENT_LENGTH, String.valueOf(length));
    }

    /**
     * Returns {@code true} if and only if the specified message contains the
     * {@code "Expect: 100-continue"} header.
     *
     * @param message message
     * @return is100ContinueExpected
     */
    public static boolean is100ContinueExpected(HttpRequest message) {
        // Expect: 100-continue is for requests only.
        if (message == null) {
            return false;
        }

        // It works only on HTTP/1.1 or later.
        if (message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }

        // In most cases, there will be one or zero 'Expect' header.
        CharSequence value = message.headers().get(HttpHeaderConstants.EXPECT);
        if (value == null) {
            return false;
        }
        if (HttpHeaderConstants.CONTINUE.toString().equalsIgnoreCase(String.valueOf(value))) {
            return true;
        }

        // Multiple 'Expect' headers.  Search through them.
        return message.headers().contains(HttpHeaderConstants.EXPECT, HttpHeaderConstants.CONTINUE, true);
    }

    /**
     * Sets or removes the {@code "Expect: 100-continue"} header to / from the
     * specified message.  If the specified {@code value} is {@code true},
     * the {@code "Expect: 100-continue"} header is set and all other previous
     * {@code "Expect"} headers are removed.  Otherwise, all {@code "Expect"}
     * headers are removed completely.
     *
     * @param message  message
     * @param expected expected
     */
    public static void set100ContinueExpected(HttpMessage message, boolean expected) {
        if (expected) {
            message.headers().set(HttpHeaderConstants.EXPECT, HttpHeaderConstants.CONTINUE);
        } else {
            message.headers().remove(HttpHeaderConstants.EXPECT);
        }
    }

    /**
     * Checks to see if the transfer encoding in a specified {@link HttpMessage} is chunked
     *
     * @param headers The message to check
     * @return True if transfer encoding is chunked, otherwise false
     */
    public static boolean isTransferEncodingChunked(HttpHeaders headers) {
        return headers.contains(HttpHeaderConstants.TRANSFER_ENCODING, HttpHeaderConstants.CHUNKED, true);
    }

    /**
     * Sets the block transport header
     *
     * @param headers The header of the set
     * @param chunked Whether to block transmission, is to add header information, otherwise remove header information
     */
    public static void setTransferEncodingChunked(HttpHeaders headers, boolean chunked) {
        if (chunked) {
            headers.set(HttpHeaderConstants.TRANSFER_ENCODING, HttpHeaderConstants.CHUNKED);
            headers.remove(HttpHeaderConstants.CONTENT_LENGTH);
        } else {
            List values = headers.getAll(HttpHeaderConstants.TRANSFER_ENCODING);
            if (values.isEmpty()) {
                return;
            }
            Iterator valuesIt = values.iterator();
            while (valuesIt.hasNext()) {
                String value = String.valueOf(valuesIt.next());
                if (HttpHeaderConstants.CHUNKED.toString().equalsIgnoreCase(value)) {
                    valuesIt.remove();
                }
            }
            if (values.isEmpty()) {
                headers.remove(HttpHeaderConstants.TRANSFER_ENCODING);
            } else {
                headers.set(HttpHeaderConstants.TRANSFER_ENCODING, values);
            }
        }
    }

    static void encodeAscii0(CharSequence seq, ByteBuf buf) {
        int length = seq.length();
        for (int i = 0; i < length; i++) {
            buf.writeByte((byte) seq.charAt(i));
        }
    }

    private static boolean isToken(int c) {
        // Fast for correct values, slower for incorrect ones
        if (c < 0 || c >= IS_TOKEN.length) {
            return false;
        }
        return IS_TOKEN[c];
    }


    // Skip any LWS and position to read the next character. The next character
    // is returned as being able to 'peek()' it allows a small optimisation in
    // some cases.
    private static int skipLws(Reader input) throws IOException {
        input.mark(1);
        int c = input.read();
        while (c == 32 || c == 9 || c == 10 || c == 13) {
            input.mark(1);
            c = input.read();
        }
        input.reset();
        return c;
    }

    private static SkipResult skipConstant(Reader input, String constant) throws IOException {
        int len = constant.length();

        skipLws(input);
        input.mark(len);
        int c = input.read();

        for (int i = 0; i < len; i++) {
            if (i == 0 && c == -1) {
                return SkipResult.EOF;
            }
            if (c != constant.charAt(i)) {
                input.reset();
                return SkipResult.NOT_FOUND;
            }
            if (i != (len - 1)) {
                c = input.read();
            }
        }
        return SkipResult.FOUND;
    }

    /**
     * @return the token if one was found, the empty string if no data was
     * available to read or <code>null</code> if data other than a
     * token was found
     */
    private static String readToken(Reader input) throws IOException {
        StringBuilder result = new StringBuilder();

        skipLws(input);
        input.mark(1);
        int c = input.read();

        while (c != -1 && isToken(c)) {
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }
        // Use mark(1)/reset() rather than skip(-1) since skip() is a NOP
        // once the end of the String has been reached.
        input.reset();

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    /**
     * @return the digits if any were found, the empty string if no data was
     * found or if data other than digits was found
     */
    private static String readDigits(Reader input) throws IOException {
        StringBuilder result = new StringBuilder();

        skipLws(input);
        input.mark(1);
        int c = input.read();

        while (c != -1 && Character.isDigit(c)) {
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }
        // Use mark(1)/reset() rather than skip(-1) since skip() is a NOP
        // once the end of the String has been reached.
        input.reset();

        return result.toString();
    }

    /**
     * @return the number if digits were found, -1 if no data was found
     * or if data other than digits was found
     */
    private static long readLong(Reader input) throws IOException {
        String digits = readDigits(input);
        if (digits.length() == 0) {
            return -1;
        }
        return Long.parseLong(digits);
    }

    public enum SkipResult {
        FOUND,
        NOT_FOUND,
        EOF
    }

    public static class Entry {
        public final long start;
        public final long end;

        public Entry(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class Ranges {
        public final String units;
        public final List<Entry> entries;

        private Ranges(String units, List<Entry> entries) {
            this.units = units;
            this.entries = entries;
        }

        /**
         * Parses a Range header from an HTTP header.
         *
         * @param input a reader over the header text
         * @return a set of ranges parsed from the input, or null if not valid
         * @throws IOException if there was a problem reading the input
         */
        public static Ranges parse(StringReader input) throws IOException {
            // Units (required)
            String units = readToken(input);
            if (units == null || units.isEmpty()) {
                return null;
            }

            // Must be followed by '='
            if (skipConstant(input, "=") == SkipResult.NOT_FOUND) {
                return null;
            }

            // Range entries
            List<Entry> entries = new ArrayList<>();
            SkipResult skipResult;
            do {
                long start = readLong(input);
                // Must be followed by '-'
                if (skipConstant(input, "-") == SkipResult.NOT_FOUND) {
                    return null;
                }
                long end = readLong(input);
                if (start == -1 && end == -1) {
                    // Invalid range
                    return null;
                }
                entries.add(new Entry(start, end));

                skipResult = skipConstant(input, ",");
                if (skipResult == SkipResult.NOT_FOUND) {
                    // Invalid range
                    return null;
                }
            } while (skipResult == SkipResult.FOUND);

            // There must be at least one entry
            if (entries.isEmpty()) {
                return null;
            }
            return new Ranges(units, entries);
        }
    }
}
