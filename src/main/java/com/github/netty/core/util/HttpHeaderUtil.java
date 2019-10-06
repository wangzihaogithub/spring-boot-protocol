package com.github.netty.core.util;

import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

import java.util.Iterator;
import java.util.List;

/**
 * @author wangzihao
 */
public class HttpHeaderUtil {

    public static boolean isFormUrlEncoder(String contentType) {
        if(contentType == null){
            return false;
        }
        return contentType.contains("application/x-www-form-urlencoded");
    }

    /**
     * 移除头部不支持拖挂的字段
     * @param lastHttpContent 最后一次内容
     */
    public static void removeHeaderUnSupportTrailer(LastHttpContent lastHttpContent) {
        if(lastHttpContent == null){
            return;
        }
        if(lastHttpContent == LastHttpContent.EMPTY_LAST_CONTENT){
            return;
        }
        HttpHeaders headers = lastHttpContent.trailingHeaders();
        if(headers.isEmpty()){
            return;
        }
        headers.remove(HttpHeaderConstants.TRANSFER_ENCODING.toString());
        headers.remove(HttpHeaderConstants.CONTENT_LENGTH.toString());
        headers.remove(HttpHeaderConstants.TRAILER.toString());
    }

    /**
     * 是否接受分段传输
     * @param headers headers
     * @return boolean
     */
    public static boolean isAcceptTransferChunked(HttpHeaders headers){
        String transferEncodingValue = headers.get(HttpHeaderConstants.TE);
        if(transferEncodingValue == null || transferEncodingValue.isEmpty()){
            return true;
        }
        return headers.contains(HttpHeaderConstants.TE, HttpHeaderConstants.CHUNKED.toString(),true);
    }

    /**
     * Returns {@code true} if and only if the connection can remain open and
     * thus 'kept alive'.  This methods respects the value of the
     * {@code "Connection"} header first and then the return value of
     * {@link HttpVersion#isKeepAliveDefault()}.
     * @param message message
     * @return boolean isKeepAlive
     */
    public static boolean isKeepAlive(HttpRequest message) {
        HttpHeaders headers = message.headers();

        Object connectionValueObj = headers.get(HttpHeaderConstants.CONNECTION);

        //如果协议支持
        if (message.getProtocolVersion().isKeepAliveDefault()) {
            //不包含close就是保持
            return StringUtil.isEmpty(connectionValueObj) ||
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
     *     <ul>
     *     <li>set to {@code "close"} if {@code keepAlive} is {@code false}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * <li>If the connection is closed by default:
     *     <ul>
     *     <li>set to {@code "keep-alive"} if {@code keepAlive} is {@code true}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * </ul>
     * @param message message
     * @param keepAlive keepAlive
     */
    public static void setKeepAlive(HttpResponse message, boolean keepAlive) {
        HttpHeaders h = message.headers();
        if (message.protocolVersion().isKeepAliveDefault()) {
            if (keepAlive) {
                h.remove(HttpHeaderConstants.CONNECTION);
            } else {
//                h.set(HttpHeaderConstants.CONNECTION, HttpHeaderConstants.CLOSE);
            }
        } else {
            if (keepAlive) {
                h.set(HttpHeaderConstants.CONNECTION, HttpHeaderConstants.KEEP_ALIVE);
            } else {
                h.remove(HttpHeaderConstants.CONNECTION);
            }
        }
    }

    /**
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length
     *
     * @throws NumberFormatException
     *         if the message does not have the {@code "Content-Length"} header
     *         or its value is not a number
     * @param message message
     */
    public static long getContentLength(HttpMessage message) {
        Long value = TypeUtil.castToLong(message.headers().get(HttpHeaderConstants.CONTENT_LENGTH));
        if (value != null) {
            return value;
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength(message);
        if (webSocketContentLength >= 0) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        throw new NumberFormatException("header not found: " + HttpHeaderConstants.CONTENT_LENGTH);
    }

    /**
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length or {@code defaultValue} if this message does
     *         not have the {@code "Content-Length"} header or its value is not
     *         a number
     * @param message message
     * @param defaultValue defaultValue
     */
    public static long getContentLength(HttpMessage message, long defaultValue) {
        Long value = TypeUtil.castToLong(message.headers().get(HttpHeaderConstants.CONTENT_LENGTH));
        if (value != null) {
            return value;
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength(message);
        if (webSocketContentLength >= 0) {
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
     * @param headers headers
     * @param length length
     */
    public static void setContentLength(HttpHeaders headers, long length) {
        headers.set(HttpHeaderConstants.CONTENT_LENGTH, (CharSequence)String.valueOf(length));
    }

    /**
     * Returns {@code true} if and only if the specified message contains the
     * {@code "Expect: 100-continue"} header.
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
     * @param message message
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
     * @param headers The message to check
     * @return True if transfer encoding is chunked, otherwise false
     */
    public static boolean isTransferEncodingChunked(HttpHeaders headers) {
        return headers.contains(HttpHeaderConstants.TRANSFER_ENCODING, HttpHeaderConstants.CHUNKED, true);
    }

    /**
     * Sets the block transport header
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
                headers.set( HttpHeaderConstants.TRANSFER_ENCODING, values);
            }
        }
    }

    static void encodeAscii0(CharSequence seq, ByteBuf buf) {
        int length = seq.length();
        for (int i = 0 ; i < length; i++) {
            buf.writeByte((byte) seq.charAt(i));
        }
    }
}
