package com.github.netty.register.servlet;

import com.github.netty.register.servlet.util.HttpConstants;
import com.github.netty.register.servlet.util.HttpHeaderConstants;
import com.github.netty.core.util.AbstractRecycler;
import com.github.netty.core.util.HttpHeaderUtil;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.StringUtil;
import com.github.netty.register.servlet.util.ServletUtil;
import com.github.netty.register.servlet.util.SnowflakeIdWorker;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * servlet请求
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 *
 * @author acer01
 *  2018/7/15/015
 */
@sun.misc.Contended
public class ServletHttpServletRequest implements javax.servlet.http.HttpServletRequest,Recyclable {

    private static final AbstractRecycler<ServletHttpServletRequest> RECYCLER = new AbstractRecycler<ServletHttpServletRequest>() {
        @Override
        protected ServletHttpServletRequest newInstance() {
            return new ServletHttpServletRequest();
        }
    };

    private static final SnowflakeIdWorker SNOWFLAKE_ID_WORKER = new SnowflakeIdWorker();
    private static final Locale[] DEFAULT_LOCALS = {Locale.getDefault()};

    private ServletHttpObject httpServletObject;
    private ServletAsyncContext asyncContext;

    private String protocol;
    private String scheme;
    private String servletPath;
    private String queryString;
    private String pathInfo;
    private String requestURI;
    private String characterEncoding;
    private String sessionId;
    private SessionTrackingMode sessionIdSource;

    private boolean decodePathsFlag = false;
    private boolean decodeCookieFlag = false;
    private boolean decodeParameterByUrlFlag = false;
    private boolean decodeParameterByBodyFlag = false;
    private boolean usingInputStream = false;

    private BufferedReader reader;
    private NettyHttpRequest nettyRequest = new NettyHttpRequest();
    private ServletInputStream inputStream = new ServletInputStream();
    private Map<String,Object> attributeMap = new ConcurrentHashMap<>(16);
    private Map<String,String[]> parameterMap;
    private Cookie[] cookies;
    private Locale[] locales;
    private Boolean asyncSupportedFlag;

    protected ServletHttpServletRequest() {}

    public static ServletHttpServletRequest newInstance(ServletHttpObject httpServletObject, FullHttpRequest fullHttpRequest) {
        Objects.requireNonNull(httpServletObject);

        ServletHttpServletRequest instance = RECYCLER.getInstance();
        instance.httpServletObject = httpServletObject;
        instance.nettyRequest.wrap(fullHttpRequest);
        instance.inputStream.wrap(fullHttpRequest.content());
        return instance;
    }

    public boolean isAsync(){
        return asyncContext != null && asyncContext.isStarted();
    }

    public void setAsyncSupportedFlag(Boolean asyncSupportedFlag) {
        this.asyncSupportedFlag = asyncSupportedFlag;
    }

    public ServletHttpObject getHttpServletObject() {
        return httpServletObject;
    }

    public NettyHttpRequest getNettyRequest() {
        return nettyRequest;
    }

    public HttpHeaders getNettyHeaders() {
        return nettyRequest.headers();
    }

    private Map<String, Object> getAttributeMap() {
        return attributeMap;
    }

    private boolean isDecodeParameter(){
        return decodeParameterByBodyFlag || decodeParameterByUrlFlag;
    }

    /**
     * 解析地区
     */
    private void decodeLocale(){
        Locale[] locales;
        String headerValue = getHeader(HttpHeaderConstants.ACCEPT_LANGUAGE.toString());
        if(headerValue == null){
            locales = DEFAULT_LOCALS;
        }else {
            String[] values = headerValue.split(",");
            int length = values.length;
            locales = new Locale[length];
            for(int i=0; i< length; i++){
                String value = values[i];
                String[] valueSp = value.split(";");
                Locale locale;
                if(valueSp.length > 0) {
                    locale = Locale.forLanguageTag(valueSp[0]);
                }else {
                    locale = Locale.forLanguageTag(value);
                }
                locales[i] = locale;
            }
        }
        this.locales = locales;
    }

    /**
     * 解析编码
     */
    private void decodeCharacterEncoding() {
        String characterEncoding = ServletUtil.decodeCharacterEncoding(getContentType());
        if (characterEncoding == null) {
            characterEncoding = getServletContext().getRequestCharacterEncoding();
        }
       this.characterEncoding = characterEncoding;
    }

    /**
     * 解析参数规范
     *
     * getParameterValues方法返回一个String对象的数组，包含了与参数名称相关的所有参数值。getParameter
     * 方法的返回值必须是getParameterValues方法返回的String对象数组中的第一个值。getParameterMap方法
     * 返回请求参数的一个java.util.Map对象，其中以参数名称作为map键，参数值作为map值。
     *  查询字符串和POST请求的数据被汇总到请求参数集合中。查询字符串数据在POST数据之前发送。例如，
     * 如果请求由查询字符串a =hello 和POST数据a=goodbye&a=world 组成，得到的参数集合顺序将是 =(hello,goodbye,world)。
     * 这些API不会暴露GET请求（HTTP 1.1所定义的）的路径参数。他们必须从getRequestURI方法或getPathInfo
     * 方法返回的字符串值中解析。
     *
     * 以下是在POST表单数据填充到参数集前必须满足的条件：
     * 1。该请求是一个HTTP或HTTPS请求。
     * 2。HTTP方法是POST。
     * 3。内容类型是application/x-www-form-urlencoded。
     * 4。该servlet已经对request对象的任意getParameter方法进行了初始调用。
     * 如果不满足这些条件，而且参数集中不包括POST表单数据，那么servlet必须可以通过request对象的输入
     * 流得到POST数据。如果满足这些条件，那么从request对象的输入流中直接读取POST数据将不再有效。
     */
    private void decodeParameter(){
        Map<String,String[]> parameterMap = new HashMap<>(16);
        Charset charset = Charset.forName(getCharacterEncoding());
        ServletUtil.decodeByUrl(parameterMap, nettyRequest.uri(),charset);
        this.decodeParameterByUrlFlag = true;

        if(HttpConstants.POST.equalsIgnoreCase(getMethod())
                && getContentLength() > 0
                && HttpHeaderUtil.isFormUrlEncoder(getContentType())){
            ServletUtil.decodeByBody(parameterMap,nettyRequest,charset);
            this.decodeParameterByBodyFlag = true;
        }
        this.parameterMap = Collections.unmodifiableMap(parameterMap);
    }

    /**
     * 解析cookie
     */
    private void decodeCookie(){
        Object value = getHeader(HttpHeaderConstants.COOKIE.toString());
        if (value == null) {
            return;
        }
        this.cookies = ServletUtil.decodeCookie(value.toString());
        this.decodeCookieFlag = true;
    }

    /**
     * 解析路径
     */
    private void decodePaths(){
        // TODO: 10月16日/0016 加上pathInfo, web.xml里配置 /* 或/a/** 用的上
        ServletContext servletContext = getServletContext();
        String contextPath = servletContext.getContextPath();
        boolean existContextPath = contextPath != null && contextPath.length() > 0;

        String sourceURI = nettyRequest.uri();
        if (sourceURI.indexOf('\\') > -1) {
            sourceURI = sourceURI.replace('\\', '/');
        }
        String servletPath = existContextPath? sourceURI.replace(contextPath, "") : sourceURI;
        if (servletPath.isEmpty() || servletPath.charAt(0)!= '/') {
            servletPath = "/".concat(servletPath);
        }

        //解析queryString
        int queryInx = servletPath.indexOf('?');
        if (queryInx > -1) {
            this.queryString = servletPath.substring(queryInx + 1, servletPath.length());
            servletPath = servletPath.substring(0, queryInx);
        }

        //解析requestURI, 保证 requestURI 前缀加 /
        String requestURI;
        if(existContextPath){
            requestURI = "/".concat(contextPath).concat(servletPath);
        }else {
            requestURI = servletPath;
        }

        this.servletPath = servletPath;
        this.requestURI = requestURI;
        // 1.加上pathInfo
        this.pathInfo = null;
        this.decodePathsFlag = true;
    }

    /**
     * 新建会话ID
     * @return
     */
    private String newSessionId(){
        return String.valueOf(SNOWFLAKE_ID_WORKER.nextId());
    }

    @Override
    public Cookie[] getCookies() {
        if(decodeCookieFlag){
            return cookies;
        }
        decodeCookie();
        return cookies;
    }

    /**
     * servlet标准 :
     *
     * 返回指定请求头的值
     *作为long值，代表a
     * 日期对象。使用这种方法
     *包含日期的标头，例如
     返回日期为
     从1970年1月1日开始的毫秒数。
     头名不区分大小写。
     ，如果请求没有页眉
     *指定名称，此方法返回-1。如果消息头
     不能转换为日期，方法抛出。
     *IllegalArgumentException代码
     * @param name ，指定标题的名称
     * @return 表示指定的日期 在表示为毫秒数自1970年1月1日起，或-1，如果指定标题。未包括在请求
     */
    @Override
    public long getDateHeader(String name) throws IllegalArgumentException {
        String value = getHeader(name);
        if(StringUtil.isEmpty(value)){
            return -1;
        }

        Long timestamp = ServletUtil.parseHeaderDate(value);
        if(timestamp == null){
            throw new IllegalArgumentException(value);
        }
        return (long)timestamp;
    }

    /**
     * getHeader方法返回给定头名称的头。多个头可以具有相同的名称，例如HTTP请求中的Cache-Control头。
     * 如果多个头的名称相同，getHeader方法返回请求中的第一个头。getHeaders方法允许访问所有与特定头名
     * 称相关的头值，返回一个String对象的枚举。
     * 头可包含由String形式的int或Date数据。HttpServletRequest接口提供如下方便的方法访问这些类型的头
     * 数据：头可包含由String形式的int或Date数据。HttpServletRequest接口提供如下方便的方法访问这些类型的头
     *  getIntHeader
     *  getDateHeader
     * 如果getIntHeader方法不能转换为int的头值，则抛出NumberFormatException异常。如果getDateHeader方
     * 法不能把头转换成一个Date对象，则抛出IllegalArgumentException异常。
     * @param name
     * @return
     */
    @Override
    public String getHeader(String name) {
       Object value = getNettyHeaders().get((CharSequence) name);
        return value == null? null :String.valueOf(value);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set nameSet = getNettyHeaders().names();
        return new Enumeration<String>() {
            private Iterator iterator = nameSet.iterator();
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }
            @Override
            public String nextElement() {
                return iterator.next().toString();
            }
        };
    }

    /**
     * 摘抄tomcat的实现
     * @return
     */
    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0){
            port = HttpConstants.HTTP_PORT;
        }

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((HttpConstants.HTTP.equals(scheme) && (port != HttpConstants.HTTP_PORT))
                || (HttpConstants.HTTPS.equals(scheme) && (port != HttpConstants.HTTPS_PORT))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());
        return url;
    }

    // 现在把PathInfo恒为null，ServletPath恒为uri-contextPath
    // 可以满足SpringBoot的需求，但不满足ServletPath和PathInfo的语义
    // 需要在RequestUrlPatternMapper匹配的时候设置,new NettyRequestDispatcher的时候传入MapperData

    /**
     * PathInfo：请求路径的一部分，不属于Context Path或Servlet Path。如果没有额外的路径，它要么是null，
     * 要么是以'/'开头的字符串。
     * @return
     */
    @Override
    public String getPathInfo() {
        // TODO: 10月16日/0016 ServletPath和PathInfo应该是互补的，根据URL-Pattern匹配的路径不同而不同
        if(!decodePathsFlag){
            decodePaths();
        }
        return this.pathInfo;
    }

    @Override
    public String getQueryString() {
        if(!decodePathsFlag){
            decodePaths();
        }
        return this.queryString;
    }

    @Override
    public String getRequestURI() {
        if(!decodePathsFlag){
            decodePaths();
        }
        return this.requestURI;
    }

    /**
     * Servlet Path：路径部分直接与激活请求的映射对应。这个路径以“/”字符开头，如果请求与“/ *”或“”模式
     * 匹配，在这种情况下，它是一个空字符串。
     * @return
     */
    @Override
    public String getServletPath() {
        if(!decodePathsFlag){
            decodePaths();
        }
        return this.servletPath;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Collection collection = getNettyHeaders().getAll((CharSequence)name);
        return new Enumeration<String>() {
            private Iterator iterator = collection.iterator();
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }
            @Override
            public String nextElement() {
                return iterator.next().toString();
            }
        };
    }


    /**
     * servlet标准:
     *
     * 返回指定请求头的值
     *作为int。如果请求没有标题
     *指定的名称，此方法返回-1。如果
     该方法不能将header转换为整数
     *抛出一个NumberFormatException 代码。
     头名不区分大小写。
     * @param name String指定请求头的名称
     * @exception NumberFormatException 如果标题值不能转换一个int。
     * @return 一个表示值的整数 请求头或-1 如果请求没有此名称的页眉返回-1
     */
    @Override
    public int getIntHeader(String name) {
        String headerStringValue = getHeader(name);
        if (headerStringValue == null) {
            return -1;
        }
        return Integer.parseInt(headerStringValue);
    }
    /*====== Header 相关方法 结束 ======*/


    @Override
    public String getMethod() {
        return nettyRequest.method().toString();
    }


    /**
     * Context Path：与ServletContext相关联的路径前缀是这个servlet的一部分。如果这个上下文是基于Web
     * 服务器的URL命名空间基础上的“默认”上下文，那么这个路径将是一个空字符串。否则，如果上下文不是
     * 基于服务器的命名空间，那么这个路径以/字符开始，但不以/字符结束
     */
    @Override
    public String getContextPath() {
        return getServletContext().getContextPath();
    }

    @Override
    public ServletHttpSession getSession(boolean create) {
        ServletHttpSession httpSession = httpServletObject.getSession();
        if (httpSession != null && httpSession.isValid()) {
            return httpSession;
        }

        if (!create) {
            return null;
        }

        String sessionId = getRequestedSessionId();
        ServletContext servletContext = getServletContext();
        SessionService sessionService = servletContext.getSessionService();
        Session session = sessionService.getSession(sessionId);
        boolean newSessionFlag = session == null;

        if (newSessionFlag) {
            long currTime = System.currentTimeMillis();
            session = new Session(sessionId);
            session.setCreationTime(currTime);
            session.setLastAccessedTime(currTime);
            session.setMaxInactiveInterval(servletContext.getSessionTimeout());
        }

        if (httpSession == null) {
            httpSession = new ServletHttpSession(servletContext);
        }else {
            httpSession.setServletContext(servletContext);
        }
        httpSession.wrap(session);
        httpSession.access();
        httpSession.setNewSessionFlag(newSessionFlag);
        httpServletObject.setSession(httpSession);
        return httpSession;
    }

    @Override
    public ServletHttpSession getSession() {
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        ServletHttpSession httpSession = getSession(true);
        String oldSessionId = httpSession.getId();
        String newSessionId = newSessionId();
        ServletContext servletContext = getServletContext();

        servletContext.getSessionService().changeSessionId(oldSessionId,newSessionId);

        sessionId = newSessionId;
        httpSession.setId(sessionId);

        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasHttpSessionIdListener()){
            listenerManager.onHttpSessionIdChanged(new HttpSessionEvent(httpSession),oldSessionId);
        }
        return newSessionId;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        getRequestedSessionId();
        return sessionIdSource == SessionTrackingMode.COOKIE ||
                sessionIdSource == SessionTrackingMode.URL;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        getRequestedSessionId();
        return sessionIdSource == SessionTrackingMode.COOKIE;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return isRequestedSessionIdFromUrl();
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        getRequestedSessionId();
        return sessionIdSource == SessionTrackingMode.URL;
    }

    @Override
    public String getRequestedSessionId() {
        if(StringUtil.isNotEmpty(sessionId)){
            return sessionId;
        }

        //如果用户设置了sessionCookie名称, 则以用户设置的为准
        String userSettingCookieName = getServletContext().getSessionCookieConfig().getName();
        String cookieSessionName = StringUtil.isNotEmpty(userSettingCookieName)? userSettingCookieName : HttpConstants.JSESSION_ID_COOKIE;

        //寻找sessionCookie的值, 优先从cookie里找, 找不到再从url参数头上找
        String sessionId = ServletUtil.getCookieValue(getCookies(),cookieSessionName);
        if(StringUtil.isNotEmpty(sessionId)){
            sessionIdSource = SessionTrackingMode.COOKIE;
        }else {
            String queryString = getQueryString();
            boolean isUrlCookie = queryString != null && queryString.contains(HttpConstants.JSESSION_ID_URL);
            if(isUrlCookie) {
                sessionIdSource = SessionTrackingMode.URL;
                sessionId = getParameter(HttpConstants.JSESSION_ID_URL);
            }else {
                sessionIdSource = null;
                sessionId = newSessionId();
            }
        }

        this.sessionId = sessionId;
        return sessionId;
    }

    @Override
    public Object getAttribute(String name) {
        Object value = getAttributeMap().get(name);
        return value;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(getAttributeMap().keySet());
    }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            decodeCharacterEncoding();
        }
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        characterEncoding = env;
    }

    @Override
    public int getContentLength() {
        return (int) getContentLengthLong();
    }

    @Override
    public long getContentLengthLong() {
        return inputStream.getContentLength();
    }

    @Override
    public String getContentType() {
        return getHeader(HttpHeaderConstants.CONTENT_TYPE.toString());
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if(reader != null){
            throw new IllegalStateException("getReader() has already been called for this request");
        }
        usingInputStream = true;
        return inputStream;
    }

    @Override
    public String getParameter(String name) {
        String[] values = getParameterMap().get(name);
        if(values == null || values.length == 0){
            return null;
        }
        return values[0];
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return getParameterMap().get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if(!isDecodeParameter()) {
            decodeParameter();
        }
        return parameterMap;
    }

    @Override
    public String getProtocol() {
        if(protocol == null) {
            protocol = nettyRequest.protocolVersion().toString();
        }
        return protocol;
    }

    @Override
    public String getScheme() {
        if(scheme == null){
            scheme = String.valueOf(nettyRequest.protocolVersion().protocolName()).toLowerCase();
        }
        return scheme;
    }

    @Override
    public String getServerName() {
        InetSocketAddress inetSocketAddress = httpServletObject.getLocalAddress();
        if(inetSocketAddress != null) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        return httpServletObject.getLocalAddress().getHostName();
    }

    @Override
    public int getServerPort() {
        return httpServletObject.getServletServerAddress().getPort();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if(usingInputStream){
            throw new IllegalStateException("getInputStream() has already been called for this request");
        }
        if(reader == null){
            synchronized (this){
                if(reader == null){
                    String charset = getCharacterEncoding();
                    if(charset == null){
                        charset = getServletContext().getRequestCharacterEncoding();
                    }
                    reader = new BufferedReader(new InputStreamReader(getInputStream(),charset));
                }
            }
        }
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress inetSocketAddress = httpServletObject.getRemoteAddress();
        if(inetSocketAddress == null){
            return null;
        }
        InetAddress inetAddress = inetSocketAddress.getAddress();
        if(inetAddress == null){
            return null;
        }
        return inetAddress.getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        InetSocketAddress inetSocketAddress = httpServletObject.getRemoteAddress();
        if(inetSocketAddress == null){
            return null;
        }
        return inetSocketAddress.getHostName();
    }

    @Override
    public int getRemotePort() {
        InetSocketAddress inetSocketAddress = httpServletObject.getRemoteAddress();
        if(inetSocketAddress == null){
            return 0;
        }
        return inetSocketAddress.getPort();
    }

    @Override
    public void setAttribute(String name, Object object) {
        Objects.requireNonNull(name);

        if(object == null){
            removeAttribute(name);
            return;
        }

        Object oldObject = getAttributeMap().put(name,object);

        ServletContext servletContext = getServletContext();
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletRequestAttributeListener()){
            listenerManager.onServletRequestAttributeAdded(new ServletRequestAttributeEvent(servletContext,this,name,object));
            if(oldObject != null){
                listenerManager.onServletRequestAttributeReplaced(new ServletRequestAttributeEvent(servletContext,this,name,oldObject));
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object oldObject = getAttributeMap().remove(name);

        ServletContext servletContext = getServletContext();
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletRequestAttributeListener()){
            listenerManager.onServletRequestAttributeRemoved(new ServletRequestAttributeEvent(servletContext,this,name,oldObject));
        }
    }

    @Override
    public Locale getLocale() {
        if(this.locales == null){
            decodeLocale();
        }

        Locale[] locales = this.locales;
        if(locales == null || locales.length == 0) {
            return null;
        }
        return locales[0];
    }

    @Override
    public Enumeration<Locale> getLocales() {
        if(this.locales == null){
            decodeLocale();
        }
        return new Enumeration<Locale>() {
            private int index = 0;
            @Override
            public boolean hasMoreElements() {
                return index < locales.length;
            }
            @Override
            public Locale nextElement() {
                Locale locale = locales[index];
                index++;
                return locale;
            }
        };
    }

    @Override
    public boolean isSecure() {
        return HttpConstants.HTTPS.equals(getScheme());
    }

    @Override
    public ServletRequestDispatcher getRequestDispatcher(String path) {
        return getServletContext().getRequestDispatcher(path);
    }

    @Override
    public String getRealPath(String path) {
        return getServletContext().getRealPath(path);
    }

    @Override
    public String getLocalName() {
        return getServletContext().getServletServerAddress().getHostName();
    }

    @Override
    public String getLocalAddr() {
        return getServletContext().getServletServerAddress().getAddress().getHostAddress();
    }

    @Override
    public int getLocalPort() {
        return getServletContext().getServletServerAddress().getPort();
    }

    @Override
    public ServletContext getServletContext() {
        return httpServletObject.getServletContext();
    }

    @Override
    public ServletAsyncContext startAsync() throws IllegalStateException {
        return startAsync(this,httpServletObject.getHttpServletResponse());
    }

    @Override
    public ServletAsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        if(!isAsyncSupported()){
            throw new IllegalStateException("不支持异步");
        }

        ServletContext servletContext = getServletContext();
        if(asyncContext == null) {
            asyncContext = new ServletAsyncContext(httpServletObject, servletContext, servletContext.getAsyncExecutorService(), servletRequest, servletResponse);
        }
        asyncContext.setTimeout(servletContext.getAsyncTimeout());
        return asyncContext;
    }

    @Override
    public boolean isAsyncStarted() {
        return asyncContext != null && asyncContext.isStarted();
    }

    @Override
    public boolean isAsyncSupported() {
        if (asyncSupportedFlag == null) {
            return true;
        }
        return asyncSupportedFlag;
    }

    @Override
    public ServletAsyncContext getAsyncContext() {
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String getPathTranslated() {
        ServletContext servletContext = getServletContext();
        String contextPath = servletContext.getContextPath();
        if (contextPath == null || contextPath.isEmpty()) {
            return null;
        }

        String pathInfo = getPathInfo();
        if (pathInfo == null) {
            return null;
        }

        return servletContext.getRealPath(pathInfo);
    }

    /**
     * "BASIC", or "DIGEST", or "SSL".
     * @return 身份验证类型
     */
    @Override
    public String getAuthType() {
        // TODO: 10月16日/0016 身份验证 : 获取验证类型
        return null;
    }

    @Override
    public String getRemoteUser() {
        Principal principal = getUserPrincipal();
        if(principal != null){
            return principal.getName();
        }
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        // TODO: 10月16日/0016 身份验证 : 是否拥有某权限
        return false;
    }

    @Override
    public boolean authenticate(javax.servlet.http.HttpServletResponse response) throws IOException, ServletException {
        // TODO: 10月16日/0016  身份验证接口
        return true;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        // TODO: 10月16日/0016  身份验证接口 : 登录
    }

    @Override
    public void logout() throws ServletException {
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return null;
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass) throws IOException, ServletException {
        try {
//            httpServletObject.getHttpServletResponse().setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
//            httpServletObject.getHttpServletResponse().getOutputStream().close();

            T handler = httpUpgradeHandlerClass.newInstance();
            return handler;
        } catch (Exception e) {
            throw new ServletException(e.getMessage(),e);
        }
    }

    @Override
    public void recycle() {
        this.inputStream.recycle();
        this.nettyRequest.recycle();

        this.decodeParameterByUrlFlag = false;
        this.decodeParameterByBodyFlag = false;
        this.decodeCookieFlag = false;
        this.decodePathsFlag = false;
        this.usingInputStream = false;
        this.reader = null;
        this.sessionIdSource = null;
        this.protocol = null;
        this.scheme = null;
        this.servletPath = null;
        this.queryString = null;
        this.pathInfo = null;
        this.requestURI = null;
        this.characterEncoding = null;
        this.sessionId = null;
        this.parameterMap = null;
        this.cookies = null;
        this.locales = null;
        this.asyncContext = null;
        this.httpServletObject = null;
        this.reader = null;

        this.attributeMap.clear();
        RECYCLER.recycleInstance(this);
    }
}
