package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderUtil;
import com.github.netty.protocol.servlet.util.ServletUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;

import javax.servlet.http.Cookie;
import java.io.Flushable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.netty.protocol.servlet.util.HttpHeaderConstants.CLOSE;

/**
 * NettyHttpResponse
 * @author wangzihao
 * 2018/7/28/028
 */
public class NettyHttpResponse implements HttpResponse, Recyclable, Flushable {
    public static final HttpResponseStatus DEFAULT_STATUS = HttpResponseStatus.OK;
    private static final String APPEND_CONTENT_TYPE = ";" + HttpHeaderConstants.CHARSET + "=";
    private DecoderResult decoderResult;
    private HttpVersion version;
    private HttpHeaders headers;
    private HttpResponseStatus status;
    private LastHttpContent lastHttpContent;
    private ServletHttpExchange exchange;
    protected final AtomicBoolean isSettingResponse = new AtomicBoolean(false);

    public NettyHttpResponse() {
        this.headers = new DefaultHttpHeaders(false);
        this.version = HttpVersion.HTTP_1_1;
        this.status = DEFAULT_STATUS;
        this.decoderResult = DecoderResult.SUCCESS;
    }

    /**
     * enableTransferEncodingChunked
     * @return LastHttpContent
     */
    public LastHttpContent enableTransferEncodingChunked(){
        if(!isTransferEncodingChunked()){
            HttpHeaderUtil.setTransferEncodingChunked(headers,true);
            lastHttpContent = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER,false);
        }
        return lastHttpContent;
    }

    public boolean isTransferEncodingChunked(){
        return HttpHeaderUtil.isTransferEncodingChunked(headers);
    }

    void setExchange(ServletHttpExchange exchange) {
        this.version = exchange.getRequest().getNettyRequest().protocolVersion();
        this.exchange = exchange;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return version;
    }

    @Override
    public DecoderResult getDecoderResult() {
        return decoderResult;
    }

    public HttpResponseStatus status() {
        return status;
    }

    public HttpVersion protocolVersion() {
        return version;
    }

    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public NettyHttpResponse setStatus(HttpResponseStatus status) {
        this.status = status;
        return this;
    }

    public LastHttpContent getLastHttpContent() {
        return lastHttpContent;
    }

    @Override
    public NettyHttpResponse setProtocolVersion(HttpVersion version) {
        this.version = version;
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        this.decoderResult = result;
    }

    @Override
    public void recycle() {
        this.headers.clear();
        this.version = HttpVersion.HTTP_1_1;
        this.status = DEFAULT_STATUS;
        this.lastHttpContent = null;
        this.decoderResult = DecoderResult.SUCCESS;
        this.isSettingResponse.set(false);
    }

    @Override
    public int hashCode() {
        int result = decoderResult != null ? decoderResult.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (headers != null ? headers.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o){
            return true;
        }
        if (o == null || getClass() != o.getClass()){
            return false;
        }

        NettyHttpResponse that = (NettyHttpResponse) o;
        if (!decoderResult.equals(that.decoderResult)){
            return false;
        }
        if (!version.equals(that.version)){
            return false;
        }
        if (!status.equals(that.status)){
            return false;
        }
        if (!headers.equals(that.headers)){
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "NettyHttpResponse{" +
                "decoderResult=" + decoderResult +
                ", version=" + version +
                ", headers=" + headers +
                ", status=" + status +
                '}';
    }

    @Override
    public void flush() {
        if(isSettingResponse.compareAndSet(false,true)) {
            //Configure the response header
            ServletHttpServletRequest servletRequest = exchange.getRequest();
            ServletHttpServletResponse servletResponse = exchange.getResponse();
            ServletSessionCookieConfig sessionCookieConfig = exchange.getServletContext().getSessionCookieConfig();

            settingResponseHeader(isKeepAlive(), servletRequest,
                    servletResponse.getContentType(), servletResponse.getCharacterEncoding(),
                    servletResponse.getContentLength(), servletResponse.getLocaleUse(),
                    servletResponse.getCookies(), sessionCookieConfig);
        }
    }

    public boolean isKeepAlive() {
        return exchange.isHttpKeepAlive()
                && !statusDropsConnection(exchange.getResponse().getStatus());
    }


    /**
     * Determine if we must drop the connection because of the HTTP status
     * code.  Use the same list of codes as Apache/httpd.
     * @param status response status
     * @return is need close.  true = need close
     */
    private static boolean statusDropsConnection(int status) {
//        if(status == 200){
//            return false;
//        }
        return
//                status == 400 /* SC_BAD_REQUEST */ ||
//                status == 408 /* SC_REQUEST_TIMEOUT */ ||
//                status == 411 /* SC_LENGTH_REQUIRED */ ||
//                status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
//                status == 414 /* SC_REQUEST_URI_TOO_LONG */ ||
//                status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
//                status == 501 /* SC_NOT_IMPLEMENTED */ ||
                status == 503 /* SC_SERVICE_UNAVAILABLE */ ;
    }

    /**
     * Set the response header
     * @param isKeepAlive isKeepAlive
     * @param servletRequest servletRequest
     * @param contentType contentType
     * @param characterEncoding characterEncoding
     * @param contentLength contentLength
     * @param locale locale
     * @param cookies cookies
     * @param sessionCookieConfig sessionCookieConfig
     */
    private void settingResponseHeader(boolean isKeepAlive,
                                       ServletHttpServletRequest servletRequest,
                                       String contentType, String characterEncoding, long contentLength, Locale locale, List<Cookie> cookies,
                                       ServletSessionCookieConfig sessionCookieConfig) {
        HttpHeaderUtil.setKeepAlive(this, isKeepAlive);
        HttpHeaders headers = headers();

        //Content length
        if(contentLength >= 0) {
            headers.remove(HttpHeaderConstants.TRANSFER_ENCODING);
            headers.set(HttpHeaderConstants.CONTENT_LENGTH, contentLength);
        }else {
            enableTransferEncodingChunked();
        }

        //if need close client
        if(servletRequest.getInputStream0().isNeedCloseClient()){
            headers.set(HttpHeaderConstants.CONNECTION, CLOSE);
        }

        // Time and date response header
        if(!headers.contains(HttpHeaderConstants.DATE)) {
            headers.set(HttpHeaderConstants.DATE, ServletUtil.getDateByRfcHttp());
        }

        //Content Type The content of the response header
        if (null != contentType) {
            String value = (null == characterEncoding) ? contentType :
                    RecyclableUtil.newStringBuilder()
                            .append(contentType)
                            .append(APPEND_CONTENT_TYPE)
                            .append(characterEncoding).toString();
            headers.set(HttpHeaderConstants.CONTENT_TYPE, value);
        }

        //Server information response header
        String serverHeader = servletRequest.getServletContext().getServerHeader();
        if(serverHeader != null && serverHeader.length() > 0) {
            headers.set(HttpHeaderConstants.SERVER, serverHeader);
        }

        //language
        if(locale != null && !headers.contains(HttpHeaderConstants.CONTENT_LANGUAGE)){
            headers.set(HttpHeaderConstants.CONTENT_LANGUAGE,locale.toLanguageTag());
        }

        // Cookies processing
        //Session is handled first. If it is a new Session and the Session id is not the same as the Session id passed by the request, it needs to be written through the Cookie
        ServletHttpSession httpSession = servletRequest.getSession(false);
        if (httpSession != null && httpSession.isNew()
//		        && !httpSession.getId().equals(servletRequest.getRequestedSessionId())
        ) {
            String sessionCookieName = sessionCookieConfig.getName();
            if(sessionCookieName == null || sessionCookieName.isEmpty()){
                sessionCookieName = HttpConstants.JSESSION_ID_COOKIE;
            }
            String sessionCookiePath = sessionCookieConfig.getPath();
            if(sessionCookiePath == null || sessionCookiePath.isEmpty()) {
                sessionCookiePath = HttpConstants.DEFAULT_SESSION_COOKIE_PATH;
            }
            String sessionCookieText = ServletUtil.encodeCookie(sessionCookieName,servletRequest.getRequestedSessionId(), -1,
                    sessionCookiePath,sessionCookieConfig.getDomain(),sessionCookieConfig.isSecure(), Boolean.TRUE);
            headers.add(HttpHeaderConstants.SET_COOKIE, sessionCookieText);

            httpSession.setNewSessionFlag(false);
        }

        //Cookies set by other businesses or frameworks are written to the response header one by one
        int cookieSize = cookies.size();
        if(cookieSize > 0) {
            for (int i=0; i<cookieSize; i++) {
                Cookie cookie = cookies.get(i);
                String cookieText = ServletUtil.encodeCookie(cookie.getName(),cookie.getValue(),cookie.getMaxAge(),cookie.getPath(),cookie.getDomain(),cookie.getSecure(),cookie.isHttpOnly());
                headers.add(HttpHeaderConstants.SET_COOKIE, cookieText);
            }
        }
    }

}
