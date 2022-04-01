package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.MediaType;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Servlet response
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletHttpServletResponse implements javax.servlet.http.HttpServletResponse, Recyclable {
    private static final Recycler<ServletHttpServletResponse> RECYCLER = new Recycler<>(ServletHttpServletResponse::new);

    private ServletHttpExchange servletHttpExchange;
    private PrintWriter writer;
    private String contentType;
    private String characterEncoding;
    private Locale locale;
    private boolean commitFlag = false;
    private long contentLength = -1;
    private int bufferSize = -1;
    private final ServletOutputStreamWrapper outputStream = new ServletOutputStreamWrapper(new CloseListener());
    private final NettyHttpResponse nettyResponse = new NettyHttpResponse();
    private final List<Cookie> cookies = new ArrayList<>();
    private final AtomicInteger errorState = new AtomicInteger(0);

    protected ServletHttpServletResponse() {}

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

    /**
     * Servlet response
     * @throws IllegalStateException
     */
    private void checkCommitted() throws IllegalStateException {
        if(isCommitted()) {
            throw new IllegalStateException("Cannot call sendError() after the response has been committed");
        }
    }

    /**
     * The header special field is checked, and if it is a special field, it is processed
     * @param name Special field
     * @param value value
     * @return True = processed, false= not processed
     */
    private boolean checkSpecialHeader(String name, String value) {
        if (HttpHeaderConstants.CONTENT_TYPE.toString().equalsIgnoreCase(name)) {
            setContentType(value);
            return true;
        }
        if(HttpHeaderConstants.CONTENT_LENGTH.toString().equalsIgnoreCase(name)) {
            try {
                long cL = Long.parseLong( value );
                setContentLengthLong(cL);
                return true;
            } catch( NumberFormatException ex ) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        }
        return false;
    }

    /**
     * Add header fields (only one field and one value is supported)
     * @param name
     * @param value
     */
    private void setHeaderObject(String name, Object value){
        if (name == null || name.length() == 0 || value == null) {
            return;
        }
        if (isCommitted()) {
            return;
        }

        //Reduce judgment time and improve efficiency
        char c = name.charAt(0);
        if ('C' == c || 'c' == c) {
            if (checkSpecialHeader(name, value.toString())) {
                return;
            }
        }
        getNettyHeaders().set((CharSequence)name, value);
    }

    /**
     * Add header fields (support multiple values for one field)
     * @param name
     * @param value
     */
    private void addHeaderObject(String name, Object value){
        if (name == null || name.length() == 0 || value == null) {
            return;
        }
        if (isCommitted()) {
            return;
        }
        //Reduce judgment time and improve efficiency
        char c = name.charAt(0);
        if ('C' == c || 'c' == c) {
            if (checkSpecialHeader(name, value.toString())) {
                return;
            }
        }

        getNettyHeaders().add((CharSequence) name, value);
    }

    private HttpHeaders getNettyHeaders(){
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
        if(!servletHttpExchange.getRequest().isRequestedSessionIdFromCookie()){
            //If the Session ID comes from a Cookie, then the client definitely supports cookies and does not need to rewrite the URL
            return url;
        }
        return url + ";" + HttpConstants.JSESSION_ID_URL + "=" + servletHttpExchange.getRequest().getRequestedSessionId();
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
        if(contentType == null){
            setContentType("text/html");
        }
    }

    @Override
    public void sendError(int sc) throws IOException {
        checkCommitted();
        nettyResponse.setStatus(HttpResponseStatus.valueOf(sc));
        resetBuffer();
        setError();
        if(contentType == null){
            setContentType("text/html");
        }
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        checkCommitted();
        nettyResponse.setStatus(HttpResponseStatus.FOUND);
        getNettyHeaders().set(HttpHeaderConstants.LOCATION, (CharSequence)location);
        commitFlag = true;
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeaderObject(name,date);
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeaderObject(name,date);
    }

    @Override
    public void setHeader(String name, String value) {
        setHeaderObject(name,value);
    }

    @Override
    public void addHeader(String name, String value) {
        addHeaderObject(name,value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeaderObject(name,value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        if (isCommitted()) {
            return;
        }
        addHeaderObject(name,value);
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
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setStatus(int sc) {
        nettyResponse.setStatus(HttpResponseStatus.valueOf(sc));
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
    public String getHeader(String name) {
        Object value = nettyResponse.headers().get((CharSequence) name);
        return value == null? null : String.valueOf(value);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List list = nettyResponse.headers().getAll((CharSequence) name);
        List<String> stringList = new LinkedList<>();
        for(Object charSequence : list){
            stringList.add(String.valueOf(charSequence));
        }
        return stringList;
    }

    @Override
    public Collection<String> getHeaderNames() {
        Set nameSet = nettyResponse.headers().names();

        List<String> nameList = new LinkedList<>();
        for(Object charSequence : nameSet){
            nameList.add(String.valueOf(charSequence));
        }
        return nameList;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public ServletOutputStreamWrapper getOutputStream(){
        return outputStream;
    }

    @Override
    public PrintWriter getWriter(){
        if(writer != null){
            return writer;
        }

        String characterEncoding = getCharacterEncoding();
        if(characterEncoding == null || characterEncoding.isEmpty()){
            if(MediaType.isHtmlType(getContentType())){
                characterEncoding = MediaType.DEFAULT_DOCUMENT_CHARACTER_ENCODING;
            }else {
                characterEncoding = servletHttpExchange.getServletContext().getResponseCharacterEncoding();
            }
            setCharacterEncoding(characterEncoding);
        }

        writer = new ServletPrintWriter(getOutputStream(), Charset.forName(characterEncoding));
        return writer;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        if(writer != null){
            return;
        }
        characterEncoding = charset;
    }

    @Override
    public void setContentLength(int len) {
        setContentLengthLong(len);
    }

    @Override
    public void setContentLengthLong(long len) {
        contentLength = len;
    }

    @Override
    public void setBufferSize(int size) {
        this.bufferSize = size;
    }

    @Override
    public int getBufferSize() {
        if(bufferSize == -1){
            bufferSize = getServletHttpExchange().getServletContext().getMaxBufferBytes();
        }
        return bufferSize;
    }

    @Override
    public void flushBuffer() throws IOException {
        getOutputStream().flush();
    }

    @Override
    public boolean isCommitted() {
        if(commitFlag){
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
     * @param resetWriterStreamFlags True = resets the print stream, false= does not reset the print stream
     */
    public void resetBuffer(boolean resetWriterStreamFlags) {
        checkCommitted();
        if(outputStream.unwrap() == null){
            return;
        }
        outputStream.resetBuffer();
        contentLength = -1;
        if(resetWriterStreamFlags) {
            writer = null;
            characterEncoding = null;
        }
    }

    @Override
    public void setLocale(Locale loc) {
        locale = loc;
    }

    @Override
    public Locale getLocale() {
        return null == locale ? Locale.getDefault() : locale;
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

    public String getMessage(){
        return nettyResponse.getStatus().reasonPhrase();
    }

    private void setError(){
        errorState.compareAndSet(0,1);
    }

    /**
     * Listen for closed flow
     * Optimize the number of lambda instances to reduce gc times
     */
    private class CloseListener implements ChannelFutureListener {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
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
