package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderUtil;
import com.github.netty.protocol.servlet.util.MediaType;
import com.github.netty.protocol.servlet.util.ServletUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Servlet response
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletHttpServletResponse implements javax.servlet.http.HttpServletResponse, Recyclable {
    private static final Recycler<ServletHttpServletResponse> RECYCLER = new Recycler<>(ServletHttpServletResponse::new);
    private final ServletOutputStreamWrapper outputStream = new ServletOutputStreamWrapper(new CloseListener());
    private final NettyHttpResponse nettyResponse = new NettyHttpResponse();
    private final List<Cookie> cookies = new ArrayList<>();
    private final AtomicInteger errorState = new AtomicInteger(0);
    private ServletHttpExchange servletHttpExchange;
    private PrintWriter writer;
    private String contentType;
    private String characterEncoding;
    private Locale locale;
    private boolean commitFlag = false;
    private long contentLength = -1;
    private int bufferSize = -1;

    protected ServletHttpServletResponse() {
    }

    public static ServletHttpServletResponse newInstance(ServletHttpExchange servletHttpExchange) {
        Objects.requireNonNull(servletHttpExchange);

        ServletHttpServletResponse instance = RECYCLER.getInstance();
        instance.servletHttpExchange = servletHttpExchange;

        /**
         * Reception not receive request
         * https://github.com/wangzihaogithub/spring-boot-protocol/issues/2
         */
        instance.outputStream.wrap(ServletOutputStream.newInstance(servletHttpExchange));
        //------------------------
        instance.nettyResponse.setExchange(servletHttpExchange);
        return instance;
    }

    private static boolean hasPath(String uri) {
        int pos = uri.indexOf("://");
        if (pos < 0) {
            return false;
        }
        pos = uri.indexOf('/', pos + 3);
        if (pos < 0) {
            return false;
        }
        return true;
    }

    private static boolean isEncodeable(final String location, ServletHttpServletRequest hreq) {

        if (location == null) {
            return false;
        }

        // Is this an intra-document reference?
        if (location.startsWith("#")) {
            return false;
        }

        // Are we in a valid session that is not using cookies?
        final ServletHttpSession session = hreq.getSession(false);
        if (session == null) {
            return false;
        }
        if (hreq.isRequestedSessionIdFromCookie()) {
            return false;
        }
        ServletContext servletContext = hreq.getServletContext();

        // Is URL encoding permitted
        if (!servletContext.getEffectiveSessionTrackingModes().contains(SessionTrackingMode.URL)) {
            return false;
        }
        return doIsEncodeable(servletContext, hreq, location);
    }

    private static boolean doIsEncodeable(ServletContext context, ServletHttpServletRequest hreq, String location) {
        // Is this a valid absolute URL?
        URL url = null;
        try {
            URI uri = new URI(location);
            url = uri.toURL();
        } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            return false;
        }

        // Does this URL match down to (and including) the context path?
        if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol())) {
            return false;
        }
        if (!hreq.getServerName().equalsIgnoreCase(url.getHost())) {
            return false;
        }
        int serverPort = hreq.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(hreq.getScheme())) {
                serverPort = 443;
            } else {
                serverPort = 80;
            }
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol())) {
                urlPort = 443;
            } else {
                urlPort = 80;
            }
        }
        if (serverPort != urlPort) {
            return false;
        }

        String contextPath = context.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            String file = url.getFile();
            if (!file.startsWith(contextPath)) {
                return false;
            }
            String tok = ";" + context.getSessionUriParamName() + "=" + hreq.getRequestedSessionId();
            if (file.indexOf(tok, contextPath.length()) >= 0) {
                return false;
            }
        }

        // This URL belongs to our web application, so it is encodeable
        return true;

    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public ServletHttpExchange getServletHttpExchange() {
        return servletHttpExchange;
    }

    public NettyHttpResponse getNettyResponse() {
        return nettyResponse;
    }

    public long getContentLength() {
        return contentLength;
    }

    @Override
    public void setContentLength(int len) {
        setContentLengthLong(len);
    }

    /**
     * Servlet response
     *
     * @throws IllegalStateException
     */
    private void checkCommitted() throws IllegalStateException {
        if (isCommitted()) {
            throw new IllegalStateException("Cannot call sendError() after the response has been committed");
        }
    }

    /**
     * The header special field is checked, and if it is a special field, it is processed
     *
     * @param name  Special field
     * @param value value
     * @return True = processed, false= not processed
     */
    private boolean checkSpecialHeader(CharSequence name, Object value) {
        if (HttpHeaderUtil.contentEqualsIgnoreCase(HttpHeaderConstants.CONTENT_TYPE, name)) {
            setContentType(value.toString());
            return true;
        }
        if (HttpHeaderUtil.contentEqualsIgnoreCase(HttpHeaderConstants.CONTENT_LENGTH, name)) {
            try {
                long cL = Long.parseLong(value.toString());
                setContentLengthLong(cL);
                return true;
            } catch (NumberFormatException ex) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        }
        return false;
    }

    /**
     * Add header fields (only one field and one value is supported)
     *
     * @param name
     * @param value
     */
    private void setHeaderObject(String name, Object value) {
        if (name == null || name.isEmpty() || value == null) {
            return;
        }
        if (isCommitted()) {
            return;
        }
        CharSequence nameCharSequence = HttpHeaderConstants.cacheAsciiString(name);

        //Reduce judgment time and improve efficiency
        char c = name.charAt(0);
        if ('C' == c || 'c' == c) {
            if (checkSpecialHeader(nameCharSequence, value)) {
                return;
            }
        }
        getNettyHeaders().set(nameCharSequence, value);
    }

    /**
     * Add header fields (support multiple values for one field)
     *
     * @param name
     * @param value
     */
    private void addHeaderObject(String name, Object value) {
        if (name == null || name.isEmpty() || value == null) {
            return;
        }
        if (isCommitted()) {
            return;
        }
        //Reduce judgment time and improve efficiency
        CharSequence nameCharSequence = HttpHeaderConstants.cacheAsciiString(name);
        char c = name.charAt(0);
        if ('C' == c || 'c' == c) {
            if (checkSpecialHeader(nameCharSequence, value)) {
                return;
            }
        }

        getNettyHeaders().add(nameCharSequence, value);
    }

    private HttpHeaders getNettyHeaders() {
        return nettyResponse.headers();
    }

    @Override
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    @Override
    public boolean containsHeader(String name) {
        return nettyResponse.headers().contains((CharSequence) name);
    }

    @Override
    public String encodeURL(String url) {
        String absolute;
        try {
            absolute = toAbsolute(url);
        } catch (IllegalArgumentException iae) {
            // Relative URL
            return url;
        }
        ServletHttpServletRequest request = servletHttpExchange.getRequest();
        if (isEncodeable(absolute, request)) {
            // W3c spec clearly said
            if (url.equalsIgnoreCase("")) {
                url = absolute;
            } else if (url.equals(absolute) && !hasPath(url)) {
                url += '/';
            }
            return toEncoded(url, request.getRequestedSessionId0());
        } else {
            return url;
        }
    }

    private String toEncoded(String url, String sessionId) {
        if (url == null || sessionId == null) {
            return url;
        }

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuilder sb = new StringBuilder(path);
        if (sb.length() > 0) { // jsessionid can't be first.
            sb.append(';');
            String sessionUriParamName = servletHttpExchange.getServletContext().getSessionUriParamName();
            sb.append(sessionUriParamName);
            sb.append('=');
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return sb.toString();
    }

    @Override
    public String encodeRedirectURL(String url) {
        return encodeURL(url);
    }

    @Override
    @Deprecated
    public String encodeUrl(String url) {
        return encodeURL(url);
    }

    @Override
    @Deprecated
    public String encodeRedirectUrl(String url) {
        return encodeRedirectURL(url);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        checkCommitted();
        nettyResponse.setStatus(new HttpResponseStatus(sc, msg));
        resetBuffer();
        setError();
        if (contentType == null) {
            this.contentType = "text/html";
        }
    }

    @Override
    public void sendError(int sc) throws IOException {
        checkCommitted();
        nettyResponse.setStatus(HttpResponseStatus.valueOf(sc));
        resetBuffer();
        setError();
        if (contentType == null) {
            this.contentType = "text/html";
        }
    }

    @Override
    public void sendRedirect(String location) {
        checkCommitted();
        nettyResponse.setStatus(HttpResponseStatus.FOUND);
        String locationUri;
        // Relative redirects require HTTP/1.1 or later
        ServletContext servletContext = servletHttpExchange.getServletContext();
        if (servletHttpExchange.getRequest().isSupportsRelativeRedirects() && servletContext.isUseRelativeRedirects()) {
            locationUri = location;
        } else {
            locationUri = toAbsolute(location);
        }
        getNettyHeaders().set(HttpHeaderConstants.LOCATION, locationUri);
        commitFlag = true;
    }

    private String toAbsolute(String location) {
        if (location == null) {
            return null;
        }

        boolean leadingSlash = location.startsWith("/");
        if (location.startsWith("//")) {
            ServletHttpServletRequest request = servletHttpExchange.getRequest();
            // Scheme relative
            StringBuilder redirectURLCC = new StringBuilder();
            // Add the scheme
            String scheme = request.getScheme();
            redirectURLCC.append(scheme, 0, scheme.length());
            redirectURLCC.append(':');
            redirectURLCC.append(location, 0, location.length());
            return redirectURLCC.toString();
        } else if (leadingSlash || !ServletUtil.hasScheme(location)) {
            StringBuilder redirectURLCC = new StringBuilder();
            ServletHttpServletRequest request = servletHttpExchange.getRequest();

            String scheme = request.getScheme();
            String name = request.getServerName();
            int port = request.getServerPort();

            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append("://", 0, 3);
                redirectURLCC.append(name, 0, name.length());
                if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
                    redirectURLCC.append(':');
                    String portS = port + "";
                    redirectURLCC.append(portS, 0, portS.length());
                }
                if (!leadingSlash) {
                    String relativePath = request.getRequestURI();
                    int pos = relativePath.lastIndexOf('/');
                    if (pos != -1) {
                        redirectURLCC.append(relativePath, 0, pos);
                    }
                    redirectURLCC.append('/');
                }
                redirectURLCC.append(location, 0, location.length());
//                return normalize(redirectURLCC);
            } catch (Exception e) {
                throw new IllegalArgumentException(location, e);
            }
            return redirectURLCC.toString();
        } else {
            return location;
        }
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeaderObject(name, date);
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeaderObject(name, date);
    }

    @Override
    public void setHeader(String name, String value) {
        setHeaderObject(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        addHeaderObject(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeaderObject(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        if (isCommitted()) {
            return;
        }
        addHeaderObject(name, value);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setContentType(String type) {
        if (type == null) {
            contentType = null;
            return;
        }

        MediaType mediaType = MediaType.parseFast(type);
        contentType = mediaType.toStringNoCharset();
        String charset = mediaType.getCharset();
        if (charset != null) {
            setCharacterEncoding(charset);
        }
    }

    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
        nettyResponse.setStatus(new HttpResponseStatus(sc, sm));
    }

    @Override
    public int getStatus() {
        return nettyResponse.getStatus().code();
    }

    @Override
    public void setStatus(int sc) {
        nettyResponse.setStatus(HttpResponseStatus.valueOf(sc));
    }

    @Override
    public String getHeader(String name) {
        Object value = nettyResponse.headers().get((CharSequence) name);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List list = nettyResponse.headers().getAll((CharSequence) name);
        List<String> stringList = new LinkedList<>();
        for (Object charSequence : list) {
            stringList.add(String.valueOf(charSequence));
        }
        return stringList;
    }

    @Override
    public Collection<String> getHeaderNames() {
        Set nameSet = nettyResponse.headers().names();

        List<String> nameList = new LinkedList<>();
        for (Object charSequence : nameSet) {
            nameList.add(String.valueOf(charSequence));
        }
        return nameList;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        if (writer != null) {
            return;
        }
        characterEncoding = charset;
    }

    @Override
    public ServletOutputStreamWrapper getOutputStream() {
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (writer != null) {
            return writer;
        }

        String characterEncoding = getCharacterEncoding();
        if (characterEncoding == null || characterEncoding.isEmpty()) {
            if (MediaType.isHtmlType(getContentType())) {
                characterEncoding = MediaType.DEFAULT_DOCUMENT_CHARACTER_ENCODING;
            } else {
                characterEncoding = servletHttpExchange.getServletContext().getResponseCharacterEncoding();
            }
            setCharacterEncoding(characterEncoding);
        }

        writer = new ServletPrintWriter(getOutputStream(), Charset.forName(characterEncoding));
        return writer;
    }

    @Override
    public void setContentLengthLong(long len) {
        contentLength = len;
    }

    @Override
    public int getBufferSize() {
        if (bufferSize == -1) {
            bufferSize = getServletHttpExchange().getServletContext().getMaxBufferBytes();
        }
        return bufferSize;
    }

    @Override
    public void setBufferSize(int size) {
        this.bufferSize = size;
    }

    @Override
    public void flushBuffer() throws IOException {
        getOutputStream().flush();
    }

    @Override
    public boolean isCommitted() {
        if (commitFlag) {
            return true;
        }
        ServletOutputStream out = outputStream.unwrap();
        return out != null && out.isClosed();
    }

    /**
     * Reset response (full reset, head, state, response buffer)
     */
    @Override
    public void reset() {
        checkCommitted();
        resetHeader();
        resetBuffer();
    }

    /**
     * Reset response (only reset response buffer)
     */
    @Override
    public void resetBuffer() {
        resetBuffer(false);
    }

    public void resetHeader() {
        nettyResponse.headers().clear();
        nettyResponse.setStatus(NettyHttpResponse.DEFAULT_STATUS);
        cookies.clear();
        contentType = null;
        locale = null;
    }

    /**
     * Whether to reset the print stream
     *
     * @param resetWriterStreamFlags True = resets the print stream, false= does not reset the print stream
     */
    public void resetBuffer(boolean resetWriterStreamFlags) {
        checkCommitted();
        if (outputStream.unwrap() == null) {
            return;
        }
        outputStream.resetBuffer();
        contentLength = -1;
        if (resetWriterStreamFlags) {
            writer = null;
            characterEncoding = null;
        }
    }

    @Override
    public Locale getLocale() {
        return null == locale ? Locale.getDefault() : locale;
    }

    @Override
    public void setLocale(Locale loc) {
        locale = loc;
    }

    public Locale getLocaleUse() {
        return locale;
    }

    @Override
    public <T> void recycle(Consumer<T> consumer) {
        //1. Close the output stream first; 2.(by calling back CloseListener) recycle the netty response; 3
        outputStream.recycle(consumer);
    }

    public boolean isError() {
        return errorState.get() > 0;
    }

    public String getMessage() {
        return nettyResponse.getStatus().reasonPhrase();
    }

    private void setError() {
        errorState.compareAndSet(0, 1);
    }

    /**
     * Listen for closed flow
     * Optimize the number of lambda instances to reduce gc times
     */
    private class CloseListener implements ChannelFutureListener {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            ServletHttpExchange exchange = servletHttpExchange;
            if (exchange != null && exchange.isAbort()) {
                return;
            }
            nettyResponse.recycle();
            errorState.set(0);
            bufferSize = -1;
            contentLength = -1;
            servletHttpExchange = null;
            writer = null;
            cookies.clear();
            contentType = null;
            characterEncoding = null;
            locale = null;
            commitFlag = false;
            ServletHttpServletResponse.RECYCLER.recycleInstance(ServletHttpServletResponse.this);
        }
    }
}
