package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.util.SnowflakeIdWorker;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.multipart.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The servlet request
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletHttpServletRequest implements HttpServletRequest, Recyclable {
    private static final Recycler<ServletHttpServletRequest> RECYCLER = new Recycler<>(ServletHttpServletRequest::new);
    private static final Locale[] DEFAULT_LOCALS = {Locale.getDefault()};
    private static final String RFC1123_DATE = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final SimpleDateFormat[] FORMATS_TEMPLATE = {
            new SimpleDateFormat(RFC1123_DATE, Locale.ENGLISH),
            new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.ENGLISH),
            new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.ENGLISH)
    };
    private static final Map<String, ResourceManager> RESOURCE_MANAGER_MAP = new HashMap<>(2);
	private SnowflakeIdWorker snowflakeIdWorker = new SnowflakeIdWorker();
    private ServletHttpExchange servletHttpExchange;
    private ServletAsyncContext asyncContext;

    private String serverName;
    private int serverPort;
    private String remoteHost;
    private String protocol;
    private String scheme;
    private String servletPath;
    private String queryString;
    private String pathInfo;
    private String requestURI;
    private String characterEncoding;
    private String sessionId;
    private SessionTrackingMode sessionIdSource;
    private MultipartConfigElement multipartConfigElement;
    private ServletSecurityElement servletSecurityElement;

    private boolean decodePathsFlag = false;
    private boolean decodeCookieFlag = false;
    private boolean decodeParameterByUrlFlag = false;
    private InterfaceHttpPostRequestDecoder postRequestDecoder = null;
    private boolean remoteSchemeFlag = false;
    private boolean usingInputStreamFlag = false;

    private BufferedReader reader;
    private FullHttpRequest nettyRequest;
    private ServletInputStreamWrapper inputStream = new ServletInputStreamWrapper();
    private Map<String,Object> attributeMap = Collections.synchronizedMap(new HashMap<>(16));
    private LinkedMultiValueMap<String,String> parameterMap = new LinkedMultiValueMap<>(16);
    private Map<String,String[]> unmodifiableParameterMap = new AbstractMap<String, String[]>() {
	    @Override
	    public Set<Entry<String, String[]>> entrySet() {
	    	if(isEmpty()){
	    		return Collections.emptySet();
		    }
		    HashSet<Entry<String, String[]>> result = new HashSet<>(6);
		    Set<Entry<String, List<String>>> entries = parameterMap.entrySet();
		    for (Entry<String,List<String>> entry : entries) {
			    List<String> value = entry.getValue();
			    String[] valueArr = value != null? value.toArray(new String[value.size()]): null;
			    result.add(new SimpleImmutableEntry<>(entry.getKey(),valueArr));
		    }
		    return result;
	    }

	    @Override
	    public String[] get(Object key) {
		    List<String> value = parameterMap.get(key);
		    if(value == null){
		    	return null;
		    }else {
			    return value.toArray(new String[value.size()]);
		    }
	    }

	    @Override
	    public boolean containsKey(Object key) {
		    return parameterMap.containsKey(key);
	    }

	    @Override
	    public boolean containsValue(Object value) {
		    return parameterMap.containsValue(value);
	    }

	    @Override
	    public int size() {
		    return parameterMap.size();
	    }
    };

    private List<Part> fileUploadList = new ArrayList<>();
    private Cookie[] cookies;
    private Locale[] locales;
    private Boolean asyncSupportedFlag;

    protected ServletHttpServletRequest() {}

    public static ServletHttpServletRequest newInstance(ServletHttpExchange servletHttpExchange, FullHttpRequest fullHttpRequest) {
        ServletHttpServletRequest instance = RECYCLER.getInstance();
        instance.servletHttpExchange = servletHttpExchange;
        instance.nettyRequest = fullHttpRequest;
        instance.inputStream.wrap(fullHttpRequest.content());
        return instance;
    }

    void setMultipartConfigElement(MultipartConfigElement multipartConfigElement) {
        this.multipartConfigElement = multipartConfigElement;
    }

    void setServletSecurityElement(ServletSecurityElement servletSecurityElement) {
        this.servletSecurityElement = servletSecurityElement;
    }

    boolean isAsync(){
        return asyncContext != null && asyncContext.isStarted();
    }

    void setAsyncSupportedFlag(Boolean asyncSupportedFlag) {
        this.asyncSupportedFlag = asyncSupportedFlag;
    }

    public ServletHttpExchange getServletHttpExchange() {
        return servletHttpExchange;
    }

    public FullHttpRequest getNettyRequest() {
        return nettyRequest;
    }

    public HttpHeaders getNettyHeaders() {
        return nettyRequest.headers();
    }

    private Map<String, Object> getAttributeMap() {
        return attributeMap;
    }

    /**
     * Parse request scheme
     */
    private void decodeScheme(){
        String proto = getHeader(HttpHeaderConstants.X_FORWARDED_PROTO.toString());
        if(HttpConstants.HTTPS.equalsIgnoreCase(proto)){
            this.scheme = HttpConstants.HTTPS;
            this.remoteSchemeFlag = true;
        }else if(HttpConstants.HTTP.equalsIgnoreCase(proto)){
            this.scheme = HttpConstants.HTTP;
            this.remoteSchemeFlag = true;
        }else {
            this.scheme = String.valueOf(nettyRequest.protocolVersion().protocolName()).toLowerCase();
            this.remoteSchemeFlag = false;
        }
    }

    /**
     * Parse area
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
     * Parsing coding
     */
    private void decodeCharacterEncoding() {
        String characterEncoding = ServletUtil.decodeCharacterEncoding(getContentType());
        if (characterEncoding == null) {
            characterEncoding = getServletContext().getRequestCharacterEncoding();
        }
       this.characterEncoding = characterEncoding;
    }

    /**
     * parse parameter specification
     *
     * the getParameterValues method returns an array of String objects containing all the parameter values associated with the parameter name. The getParameter
     The return value of the * method must be the first value in the String object array returned by the getParameterValues method. GetParameterMap method
     * returns a java.util.map object of the request parameter, with the parameter name as the Map key and the parameter value as the Map value.
     * the query string and the data from the POST request are aggregated into the set of request parameters. The query string data is sent before the POST data. For example,
     * if the request consists of the query string a= hello and the POST data a=goodbye&a=world, the resulting parameter set order will be =(hello,goodbye,world).
     * these apis do not expose the path parameters of GET requests (as defined in HTTP 1.1). They must be from the getRequestURI method or getPathInfo
     * is resolved in the string value returned by the.
     *
     * the following conditions must be met before the POST form data is populated into the parameter set:
     * 1. The request is an HTTP or HTTPS request.
     * 2. The HTTP method is POST.
     * 3. Content type is application/x-www-form-urlencoded.
     * 4. The servlet has made an initial call to any getParameter method of the request object.
     * if these conditions are not met and POST form data is not included in the parameter set, the servlet must be able to pass the input of the request object
     * stream gets POST data. If these conditions are met, it is no longer valid to read the POST data directly from the input stream of the request object.
     */
    private void decodeBody(boolean bodyPartFlag){
        Charset charset = Charset.forName(getCharacterEncoding());
        HttpDataFactory factory = getServletContext().getHttpDataFactory(charset);
        String location = null;
        int discardThreshold = 0;
        if(multipartConfigElement != null) {
            factory.setMaxLimit(multipartConfigElement.getMaxFileSize());
            location = multipartConfigElement.getLocation();
            discardThreshold = multipartConfigElement.getFileSizeThreshold();
        }

        InterfaceHttpPostRequestDecoder postRequestDecoder = HttpPostRequestDecoder.isMultipart(nettyRequest)?
                new HttpPostMultipartRequestDecoder(factory, nettyRequest, charset):
                new HttpPostStandardRequestDecoder(factory, nettyRequest, charset);
        postRequestDecoder.setDiscardThreshold(discardThreshold);

        ResourceManager resourceManager;
        if(location != null && location.length() > 0){
            resourceManager = RESOURCE_MANAGER_MAP.get(location);
            if(resourceManager == null) {
                resourceManager = new ResourceManager(location);
                RESOURCE_MANAGER_MAP.put(location,resourceManager);
            }
        }else {
            resourceManager = getServletContext().getResourceManager();
        }

        /*
         * There are three types of HttpDataType
         * Attribute, FileUpload, InternalAttribute
         */
	    this.postRequestDecoder = postRequestDecoder;
        while (true) {
        	try {
		        if (!postRequestDecoder.hasNext()) {
		        	return;
		        }
	        }catch (HttpPostRequestDecoder.EndOfDataDecoderException e){
        		return;
	        }

            InterfaceHttpData interfaceData = postRequestDecoder.next();
            switch (interfaceData.getHttpDataType()) {
                case Attribute: {
                    Attribute data = (Attribute) interfaceData;
                    String name = data.getName();
                    String value;
                    try {
                        value = data.getValue();
                    } catch (IOException e) {
                        value = "";
                    }
                    parameterMap.add(name, value);

                    if(bodyPartFlag) {
                        ServletTextPart part = new ServletTextPart(data,resourceManager);
                        fileUploadList.add(part);
                    }
                    break;
                }
                case FileUpload: {
                    FileUpload data = (FileUpload) interfaceData;
                    ServletFilePart part = new ServletFilePart(data,resourceManager);
                    fileUploadList.add(part);
                    break;
                }
                default: {
                    break;
                }
            }
        }
    }

    /**
     * Parsing URL parameters
     */
    private void decodeUrlParameter(){
        Charset charset = Charset.forName(getCharacterEncoding());
        ServletUtil.decodeByUrl(parameterMap, nettyRequest.uri(),charset);
        this.decodeParameterByUrlFlag = true;
    }

    /**
     * Parsing the cookie
     */
    private void decodeCookie(){
        String value = getHeader(HttpHeaderConstants.COOKIE.toString());
        if (value != null && value.length() > 0) {
            Collection<Cookie> nettyCookieSet = ServletUtil.decodeCookie(value);
            if(nettyCookieSet.size() > 0){
                this.cookies = nettyCookieSet.toArray(new Cookie[0]);
            }
        }
        this.decodeCookieFlag = true;
    }

    /**
     * Returns the fully qualified name of the client
     * or the last proxy that sent the request.
     * If the engine cannot or chooses not to resolve the hostname
     * (to improve performance), this method returns the dotted-string form of
     * the IP address. For HTTP servlets, same as the value of the CGI variable
     * <code>REMOTE_HOST</code>.
     * qualified name of the client
     */
    private void decodeRemoteHost(){
        if(getServletContext().isEnableLookupFlag()){
            InetSocketAddress inetSocketAddress = servletHttpExchange.getRemoteAddress();
            if (inetSocketAddress == null) {
                throw new IllegalStateException("request invalid");
            }
            try {
                this.remoteHost = InetAddress.getByName(
                        inetSocketAddress.getHostName()).getHostName();
            } catch (IOException e) {
                //Ignore
            }
        }else {
            this.remoteHost = getRemoteAddr();
        }
    }

    /**
     * decode the host name of the server to which the request was sent.
     * It is the value of the part before ":" in the <code>Host</code>
     * header value, if any, or the resolved server name, or the server IP
     * address.
     */
    private void decodeServerNameAndPort(){
        String host = getHeader(HttpHeaderConstants.HOST.toString());
        StringBuilder sb;
        if(host != null && host.length() > 0) {
            sb = RecyclableUtil.newStringBuilder();
            int i = 0, length = host.length();
            boolean hasPort = false;
            while (i < length) {
                char c = host.charAt(i);
                if (c == ':') {
                    serverName = sb.toString();
                    sb.setLength(0);
                    hasPort = true;
                }else {
                    sb.append(c);
                }
                i++;
            }
            if(hasPort && sb.length() > 0){
                serverPort = Integer.parseInt(sb.toString());
            }else {
                serverName = sb.toString();
                sb.setLength(0);
            }
        }else {
            serverName = getRemoteHost();
        }
        if(serverPort == 0) {
            String scheme = getScheme();
            if (remoteSchemeFlag) {
                if (HttpConstants.HTTPS.equalsIgnoreCase(scheme)) {
                    serverPort = HttpConstants.HTTPS_PORT;
                } else {
                    serverPort = HttpConstants.HTTP_PORT;
                }
            } else {
                serverPort = HttpConstants.HTTP_PORT;
            }
        }
    }

    /**
     * Parsing path
     */
    private void decodePaths(){
        // TODO: 10-16/0016 Add pathInfo, web. XML configuration /* or /a/*
        String requestURI = nettyRequest.uri();
        String queryString;
        int queryInx = requestURI.indexOf('?');
        if (queryInx > -1) {
            queryString = requestURI.substring(queryInx + 1);
            requestURI = requestURI.substring(0, queryInx);
        }else {
        	queryString = null;
        }
	    if(requestURI.length() > 1 && requestURI.charAt(0) == '/' && requestURI.charAt(1) == '/'){
		    requestURI = requestURI.substring(1);
	    }

        this.requestURI = requestURI;
        this.queryString = queryString;
        // 1.Plus the pathInfo
        this.pathInfo = null;
        this.decodePathsFlag = true;
    }

    /**
     * New session ID
     * @return session ID
     */
    private String newSessionId(){
        return String.valueOf(snowflakeIdWorker.nextId());
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
     * servlet standard:
     *
     * returns the value of the specified request header
     * is the long value, representing a
     * date object. Using this method
     * contains a header for the date, for example
     Return date is
     The number of milliseconds since January 1, 1970.
     The first name is case insensitive.
     , if the request does not have a header
     * specify a name, and this method returns -1. If the header Cannot convert to date,
     * @param name ，Specifies the name of the title
     * @throws IllegalArgumentException IllegalArgumentException
     * @return Indicates the specified date in milliseconds as of January 1, 1970, or -1, if a title is specified. Not included in request
     */
    @Override
    public long getDateHeader(String name) throws IllegalArgumentException {
        String value = getHeader(name);
        if(StringUtil.isEmpty(value)){
            return -1;
        }

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
            throw new IllegalArgumentException(value);
        }
        return date.getTime();
    }

    /**
     * The getHeader method returns the header for the given header name. Multiple headers can have the same name, such as the cache-control header in an HTTP request.
     * if multiple headers have the same name, the getHeader method returns the first header in the request. The getHeaders method allowed access to all names with specific headers
     * calls the associated header value and returns an enumeration of the String object.
     * the header can contain int or Date data in the form of a String. The HttpServletRequest interface provides the following convenient methods to access these types of headers
     * data: the header can contain int or Date data in the form of a String. The HttpServletRequest interface provides the following convenient methods to access these types of headers
     * getIntHeader
     * getDateHeader
     * if the getIntHeader method cannot be converted to a header value of int, then a NumberFormatException is thrown. If getDateHeader side
     * method cannot convert the head into a Date object, then an IllegalArgumentException is thrown.
     * @param name name
     * @return header value
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
     * Copy the implementation of tomcat
     * @return RequestURL
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

    // now set PathInfo to null and ServletPath to ur-contextpath
    // satisfies the requirements of SpringBoot, but not the semantics of ServletPath and PathInfo
    // need to be set when RequestUrlPatternMapper matches, pass MapperData when new NettyRequestDispatcher

    /**
     * PathInfo：Part of the request Path that is not part of the Context Path or Servlet Path. If there's no extra path, it's either null,
     * Or a string that starts with '/'.
     * @return pathInfo
     */
    @Override
    public String getPathInfo() {
        // TODO: 10-16 /0016 ServletPath and PathInfo should be complementary, depending on the url-pattern matching path
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
     * Servlet Path: the Path section corresponds directly to the mapping of the activation request. The path starts with the "/" character, if the request is in the "/ *" or "" mode."
     * matches, in which case it is an empty string.
     * @return servletPath
     */
    @Override
    public String getServletPath() {
        if(this.servletPath == null){
            String servletPath = getServletContext().getServletPath(getRequestURI());
            String contextPath = getServletContext().getContextPath();
            if(contextPath.length() > 0){
                servletPath = servletPath.replaceFirst(contextPath,"");
            }
            this.servletPath = servletPath;
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
     * servlet standard:
     *
     * returns the value of the specified request header
     * as int. If the request has no title
     * the name specified by this method returns -1. if This method does not convert headers to integers
     * throws a NumberFormatException code. The first name is case insensitive.
     * @param name  specifies the name of the request header
     * @exception NumberFormatException If the header value cannot be converted to an int。
     * @return An integer request header representing a value or -1 if the request does not return -1 for the header with this name
     */
    @Override
    public int getIntHeader(String name) {
        String headerStringValue = getHeader(name);
        if (headerStringValue == null) {
            return -1;
        }
        return Integer.parseInt(headerStringValue);
    }

    @Override
    public String getMethod() {
        return nettyRequest.method().toString();
    }


    /**
     * Context Path: the Path prefix associated with the ServletContext is part of this servlet. If the context is web-based
     * the server's URL namespace based on the "default" context, then the path will be an empty string. Otherwise, if the context is not
     * server-based namespaces, so the path starts with /, but does not end with /
     */
    @Override
    public String getContextPath() {
        return getServletContext().getContextPath();
    }

    @Override
    public ServletHttpSession getSession(boolean create) {
	    String sessionId = getRequestedSessionId();
        ServletHttpSession httpSession = servletHttpExchange.getHttpSession();
        if (httpSession != null && httpSession.isValid() && httpSession.getId().equals(sessionId)) {
            return httpSession;
        }
	    ServletContext servletContext = getServletContext();
	    SessionService sessionService = servletContext.getSessionService();
	    Session session = sessionService.getSession(sessionId);
        if (session == null && !create) {
            return null;
        }

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
        servletHttpExchange.setHttpSession(httpSession);
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

        //If the user sets the sessionCookie name, the user set the sessionCookie name
        String userSettingCookieName = getServletContext().getSessionCookieConfig().getName();
        String cookieSessionName = StringUtil.isNotEmpty(userSettingCookieName)? userSettingCookieName : HttpConstants.JSESSION_ID_COOKIE;

        //Find the value of sessionCookie first from cookie, then from url parameter
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
    public ServletInputStreamWrapper getInputStream(){
        if(reader != null){
            throw new IllegalStateException("getReader() has already been called for this request");
        }
        usingInputStreamFlag = true;
        return inputStream;
    }

    ServletInputStreamWrapper getInputStream0(){
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
        if(!decodeParameterByUrlFlag) {
            decodeUrlParameter();
        }

        if(postRequestDecoder ==null &&
                HttpConstants.POST.equalsIgnoreCase(getMethod())
                && getContentLength() > 0
                && HttpHeaderUtil.isFormUrlEncoder(getContentType())){
            decodeBody(false);
        }
        return unmodifiableParameterMap;
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
            decodeScheme();
        }
        return scheme;
    }

    @Override
    public String getServerName() {
        if(serverName == null){
            decodeServerNameAndPort();
        }
        return serverName;
    }

    @Override
    public int getServerPort() {
        if(serverPort == 0){
            decodeServerNameAndPort();
        }
        return serverPort;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if(usingInputStreamFlag){
            throw new IllegalStateException("getInputStream() has already been called for this request");
        }
        if(reader == null){
            synchronized (this){
                if(reader == null){
                    String charset = getCharacterEncoding();
                    if(charset == null){
                        charset = getServletContext().getRequestCharacterEncoding();
                    }
                    reader = new BufferedReader(new InputStreamReader(getInputStream0(),charset));
                }
            }
        }
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress inetSocketAddress = servletHttpExchange.getRemoteAddress();
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
        if(remoteHost == null) {
            decodeRemoteHost();
        }
        return remoteHost;
    }

    @Override
    public int getRemotePort() {
        InetSocketAddress inetSocketAddress = servletHttpExchange.getRemoteAddress();
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
        return getServletContext().getServerAddress().getHostName();
    }

    @Override
    public String getLocalAddr() {
        return getServletContext().getServerAddress().getAddress().getHostAddress();
    }

    @Override
    public int getLocalPort() {
        return getServletContext().getServerAddress().getPort();
    }

    @Override
    public ServletContext getServletContext() {
        return servletHttpExchange.getServletContext();
    }

    @Override
    public ServletAsyncContext startAsync() throws IllegalStateException {
        return startAsync(this,servletHttpExchange.getResponse());
    }

    @Override
    public ServletAsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        if(!isAsyncSupported()){
            throw new IllegalStateException("Asynchronous is not supported");
        }

        ServletContext servletContext = getServletContext();
        if(asyncContext == null) {
            asyncContext = new ServletAsyncContext(servletHttpExchange, servletContext, servletContext.getAsyncExecutorService(), servletRequest, servletResponse);
        }
        asyncContext.setTimeout(servletContext.getAsyncTimeout());
        asyncContext.start();
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
     * @return Authentication type
     */
    @Override
    public String getAuthType() {
        // TODO: 10-16/0016 Authentication: gets the authentication type
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
        // TODO: 10-16/0016 Authentication: whether you have a permission
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        // TODO: 10-16/0016  Authentication interface
        return true;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        // TODO: 10-16/0016  Authentication interface: login
    }

    @Override
    public void logout() throws ServletException {
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        if(postRequestDecoder == null) {
            try {
                decodeBody(true);
            } catch (CodecException e) {
                Throwable cause = getCause(e);
                if(cause instanceof IOException){
                    setAttribute(RequestDispatcher.ERROR_STATUS_CODE,HttpServletResponse.SC_BAD_REQUEST);
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION,cause);
                    throw (IOException)cause;
                }else if(cause instanceof IllegalStateException){
                    setAttribute(RequestDispatcher.ERROR_STATUS_CODE,HttpServletResponse.SC_BAD_REQUEST);
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION,cause);
                    throw (IllegalStateException)cause;
                }else if(cause instanceof IllegalArgumentException){
                    IllegalStateException illegalStateException = new IllegalStateException("HttpServletRequest.getParts() -> decodeFile() fail : " + cause.getMessage(), cause);
                    illegalStateException.setStackTrace(cause.getStackTrace());
                    setAttribute(RequestDispatcher.ERROR_STATUS_CODE,HttpServletResponse.SC_BAD_REQUEST);
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION,illegalStateException);
                    throw illegalStateException;
                }else {
                    ServletException servletException;
                    if(cause != null){
                        servletException = new ServletException("HttpServletRequest.getParts() -> decodeFile() fail : " + cause.getMessage(),cause);
                        servletException.setStackTrace(cause.getStackTrace());
                    }else {
                        servletException = new ServletException("HttpServletRequest.getParts() -> decodeFile() fail : " + e.getMessage(),e);
                        servletException.setStackTrace(e.getStackTrace());
                    }
                    setAttribute(RequestDispatcher.ERROR_STATUS_CODE,HttpServletResponse.SC_BAD_REQUEST);
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION,servletException);
                    throw servletException;
                }
            } catch (IllegalArgumentException e) {
                IllegalStateException illegalStateException = new IllegalStateException("HttpServletRequest.getParts() -> decodeFile() fail : " + e.getMessage(), e);
                illegalStateException.setStackTrace(e.getStackTrace());
                setAttribute(RequestDispatcher.ERROR_STATUS_CODE,HttpServletResponse.SC_BAD_REQUEST);
                setAttribute(RequestDispatcher.ERROR_EXCEPTION,illegalStateException);
                throw illegalStateException;
            }
        }
        return fileUploadList;
    }

    private Throwable getCause(Throwable throwable){
        if(throwable.getCause() == null){
            return null;
        }
        while (true){
            Throwable cause = throwable;
            throwable = throwable.getCause();
            if(throwable == null){
                return cause;
            }
        }
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        for (Part part : getParts()) {
            if (name.equals(part.getName())) {
                return part;
            }
        }
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass) throws IOException, ServletException {
        try {
//            servletHttpExchange.getHttpServletResponse().setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
//            servletHttpExchange.getHttpServletResponse().getOutputStream().close();

            T handler = httpUpgradeHandlerClass.newInstance();
            return handler;
        } catch (Exception e) {
            throw new ServletException(e.getMessage(),e);
        }
    }

    @Override
    public void recycle() {
	    ServletHttpSession httpSession = servletHttpExchange.getHttpSession();
	    if(httpSession != null) {
		    if (httpSession.isValid()) {
			    httpSession.save();
		    } else{
			    httpSession.remove();
		    }
		    httpSession.clear();
	    }
        this.inputStream.recycle();
        this.nettyRequest = null;
        if(this.postRequestDecoder != null) {
            this.postRequestDecoder.destroy();
            this.postRequestDecoder = null;
        }

        this.decodeParameterByUrlFlag = false;
        this.remoteSchemeFlag = false;
        this.decodeCookieFlag = false;
        this.decodePathsFlag = false;
        this.usingInputStreamFlag = false;
        this.reader = null;
        this.sessionIdSource = null;
        this.remoteHost = null;
        this.serverName = null;
        this.serverPort = 0;
        this.protocol = null;
        this.scheme = null;
        this.servletPath = null;
        this.queryString = null;
        this.pathInfo = null;
        this.requestURI = null;
        this.characterEncoding = null;
        this.sessionId = null;
        this.cookies = null;
        this.locales = null;
        this.asyncContext = null;
        this.servletHttpExchange = null;
        this.multipartConfigElement = null;
        this.servletSecurityElement = null;

        this.parameterMap.clear();
        this.fileUploadList.clear();
        this.attributeMap.clear();
        RECYCLER.recycleInstance(this);
    }
}
