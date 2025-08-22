package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.*;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.internal.PlatformDependent;

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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The servlet request
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletHttpServletRequest implements HttpServletRequest, Recyclable {
    private static final Map<String, Locale> LOCALE_CACHE_MAP = new ConcurrentHashMap<>(6);
    private static final Recycler<ServletHttpServletRequest> RECYCLER = new Recycler<>(ServletHttpServletRequest::new);
    private static final Locale[] DEFAULT_LOCALS = {Locale.getDefault()};
    private static final Map<String, ResourceManager> RESOURCE_MANAGER_MAP = new HashMap<>(2);
    private static final SnowflakeIdWorker SNOWFLAKE_ID_WORKER = new SnowflakeIdWorker();
    private final AtomicBoolean decodeBodyFlag = new AtomicBoolean();
    private final Map<String, Object> attributeMap = new LinkedHashMap<>(32);
    private final LinkedMultiValueMap<String, String> parameterMap = new LinkedMultiValueMap<>(16);
    private final Map<String, String[]> unmodifiableParameterMap = new AbstractMap<String, String[]>() {
        @Override
        public Set<String> keySet() {
            return parameterMap.keySet();
        }

        @Override
        public Collection<String[]> values() {
            if (parameterMap.isEmpty()) {
                return Collections.emptySet();
            }
            List<String[]> result = new ArrayList<>(6);
            Set<Entry<String, List<String>>> entries = parameterMap.entrySet();
            for (Entry<String, List<String>> entry : entries) {
                List<String> value = entry.getValue();
                String[] valueArr = value != null ? value.toArray(new String[value.size()]) : null;
                result.add(valueArr);
            }
            return result;
        }

        @Override
        public Set<Entry<String, String[]>> entrySet() {
            if (parameterMap.isEmpty()) {
                return Collections.emptySet();
            }
            HashSet<Entry<String, String[]>> result = new LinkedHashSet<>(6);
            Set<Entry<String, List<String>>> entries = parameterMap.entrySet();
            for (Entry<String, List<String>> entry : entries) {
                List<String> value = entry.getValue();
                String[] valueArr = value != null ? value.toArray(new String[value.size()]) : null;
                result.add(new SimpleImmutableEntry<>(entry.getKey(), valueArr));
            }
            return result;
        }

        @Override
        public String[] get(Object key) {
            List<String> value = parameterMap.get(key);
            if (value == null) {
                return null;
            } else {
                return value.toArray(new String[value.size()]);
            }
        }

        @Override
        public boolean containsKey(Object key) {
            return parameterMap.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            if (value instanceof String[]) {
                value = Arrays.asList((String[]) value);
            } else if (value instanceof String) {
                value = Collections.singletonList(value);
            }
            return parameterMap.containsValue(value);
        }

        @Override
        public int size() {
            return parameterMap.size();
        }

        @Override
        public boolean isEmpty() {
            return parameterMap.isEmpty();
        }
    };

    private final List<Part> fileUploadList = new ArrayList<>();
    ServletAsyncContext asyncContext;
    boolean isMultipart;
    HttpRequest nettyRequest;
    ServletHttpExchange httpExchange;
    private ServletHttpSession httpSession;
    private Session session;
    private DispatcherType dispatcherType = null;
    private String serverName;
    private int serverPort;
    private String remoteHost;
    private String scheme;
    private String queryString;
    private String pathInfo;
    private String requestURI;
    private String characterEncoding;
    private String sessionId;
    private SessionTrackingMode sessionIdSource;
    MultipartConfigElement multipartConfigElement;
    ServletSecurityElement servletSecurityElement;
    ServletRequestDispatcher dispatcher;
    private volatile ResourceManager resourceManager;
    private final Supplier<ResourceManager> resourceManagerSupplier = () -> {
        if (resourceManager == null) {
            synchronized (this) {
                if (resourceManager == null) {
                    String location = null;
                    if (multipartConfigElement != null) {
                        location = multipartConfigElement.getLocation();
                    }
                    ResourceManager resourceManager;
                    if (location != null && !location.isEmpty()) {
                        resourceManager = RESOURCE_MANAGER_MAP.get(location);
                        if (resourceManager == null) {
                            resourceManager = new ResourceManager(location);
                            RESOURCE_MANAGER_MAP.put(location, resourceManager);
                        }
                    } else {
                        resourceManager = httpExchange.servletContext.resourceManager;
                    }
                    this.resourceManager = resourceManager;
                }
            }
        }
        return resourceManager;
    };
    private String cookieStringValue;
    private int decodePathsQueryIndex;
    private boolean decodeCookieFlag = false;
    private boolean decodeParameterByUrlFlag = false;
    private boolean getRequestedSessionId0 = false;
    private volatile InterfaceHttpPostRequestDecoder postRequestDecoder = null;
    private boolean remoteSchemeFlag = false;
    private boolean usingInputStreamFlag = false;
    private BufferedReader reader;
    private boolean isFormUrlEncoder;
    private final Supplier<InterfaceHttpPostRequestDecoder> postRequestDecoderSupplier = () -> {
        if (!isMultipart && !isFormUrlEncoder) {
            return null;
        }
        if (this.postRequestDecoder == null) {
            synchronized (this) {
                if (this.postRequestDecoder == null) {
                    Charset charset = Charset.forName(getCharacterEncoding());
                    HttpDataFactory httpDataFactory = getHttpDataFactory(charset);
                    InterfaceHttpPostRequestDecoder postRequestDecoder;
                    if (isMultipart) {
                        postRequestDecoder = new HttpPostMultipartRequestDecoder(httpDataFactory, nettyRequest, charset);
                    } else if (isFormUrlEncoder) {
                        postRequestDecoder = new CompatibleHttpPostStandardRequestDecoder(httpDataFactory, nettyRequest, charset);
                    } else {
                        return null;
                    }
                    this.postRequestDecoder = postRequestDecoder;
                }
            }
        }
        return this.postRequestDecoder;
    };
    final ServletInputStreamWrapper inputStream = new ServletInputStreamWrapper(postRequestDecoderSupplier, resourceManagerSupplier);
    private Cookie[] cookies;
    private Locale[] locales;
    private Boolean asyncSupportedFlag;

    protected ServletHttpServletRequest() {
    }

    public static ServletHttpServletRequest newInstance(ServletHttpExchange exchange, HttpRequest httpRequest, long contentLength) {
        ServletHttpServletRequest instance = RECYCLER.getInstance();
        instance.httpExchange = exchange;
        instance.nettyRequest = httpRequest;
        try {
            instance.isMultipart = HttpPostRequestDecoder.isMultipart(httpRequest);
        } catch (DecoderException e) {
            instance.isMultipart = false;
        }
        if (instance.isMultipart) {
            instance.isFormUrlEncoder = false;
        } else {
            instance.isFormUrlEncoder = HttpHeaderUtil.isFormUrlEncoder(instance.getContentType());
        }
        instance.resourceManager = null;
        if (instance.postRequestDecoder != null) {
            try {
                instance.postRequestDecoder.destroy();
            } catch (IllegalStateException ignored) {
            }
            instance.postRequestDecoder = null;
        }

        instance.inputStream.wrap(exchange.channelHandlerContext.alloc().compositeBuffer(Integer.MAX_VALUE));
        instance.inputStream.transferEncoding = httpRequest.headers().contains(HttpHeaderConstants.TRANSFER_ENCODING);
        instance.inputStream.httpExchange = exchange;
        instance.inputStream.fileSizeThreshold = instance.getFileSizeThreshold();
        instance.inputStream.fileUploadTimeoutMs = exchange.servletContext.uploadFileTimeoutMs;
        instance.inputStream.contentLength = contentLength;
        return instance;
    }

    void setDispatcher(ServletRequestDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.asyncSupportedFlag = dispatcher.filterChain.servletRegistration.asyncSupported;
        int queryIndex = dispatcher.queryIndex;
        if (queryIndex != -1) {
            queryIndex += dispatcher.contextPath.length();
        }
        this.decodePathsQueryIndex = queryIndex;
    }

    public int getFileSizeThreshold() {
        int fileSizeThreshold;
        if (multipartConfigElement != null) {
            fileSizeThreshold = Math.max(multipartConfigElement.getFileSizeThreshold(), ServletContext.MIN_FILE_SIZE_THRESHOLD);
        } else {
            fileSizeThreshold = httpExchange.servletContext.fileSizeThreshold;
        }
        return fileSizeThreshold;
    }

    public boolean isMultipart() {
        return isMultipart;
    }

    public boolean isAsync() {
        return asyncContext != null;
    }

    public ServletHttpExchange getHttpExchange() {
        return httpExchange;
    }

    public HttpRequest getNettyRequest() {
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
    private void decodeScheme() {
        Object headerValue = nettyRequest.headers().get(HttpHeaderConstants.X_FORWARDED_PROTO);
        if (headerValue == null) {
            HttpVersion httpVersion = nettyRequest.protocolVersion();
            if (httpVersion == HttpVersion.HTTP_1_1 || httpVersion == HttpVersion.HTTP_1_0) {
                // fast
                this.scheme = "http";
            } else {
                this.scheme = String.valueOf(nettyRequest.protocolVersion().protocolName()).toLowerCase();
            }
            this.remoteSchemeFlag = false;
        } else {
            String proto = headerValue.toString();
            if (HttpConstants.HTTPS.equalsIgnoreCase(proto)) {
                this.scheme = HttpConstants.HTTPS;
                this.remoteSchemeFlag = true;
            } else if (HttpConstants.HTTP.equalsIgnoreCase(proto)) {
                this.scheme = HttpConstants.HTTP;
                this.remoteSchemeFlag = true;
            } else {
                this.scheme = String.valueOf(nettyRequest.protocolVersion().protocolName()).toLowerCase();
                this.remoteSchemeFlag = false;
            }
        }
    }

    /**
     * Parse area
     */
    private void decodeLocale() {
        Locale[] locales;
        Object headerValue = nettyRequest.headers().get(HttpHeaderConstants.ACCEPT_LANGUAGE);
        if (headerValue == null) {
            locales = DEFAULT_LOCALS;
        } else {
            String[] values = headerValue.toString().split(",");
            int length = values.length;
            locales = new Locale[length];
            for (int i = 0; i < length; i++) {
                String value = values[i];
                String[] valueSp = value.split(";", 2);
                String localeKey;
                Locale locale;
                if (valueSp.length > 0) {
                    localeKey = valueSp[0];
                } else {
                    localeKey = value;
                }
                locale = LOCALE_CACHE_MAP.computeIfAbsent(localeKey, Locale::forLanguageTag);
                locales[i] = locale;
            }
        }
        this.locales = locales;
    }

    private HttpDataFactory getHttpDataFactory(Charset charset) {
        HttpDataFactory factory = httpExchange.servletContext.getHttpDataFactory(charset);
        if (multipartConfigElement != null) {
            factory.setMaxLimit(multipartConfigElement.getMaxFileSize());
        }
        return factory;
    }

    /**
     * parse parameter specification
     * <p>
     * the getParameterValues method returns an array of String objects containing all the parameter values associated with the parameter name. The getParameter
     * The return value of the * method must be the first value in the String object array returned by the getParameterValues method. GetParameterMap method
     * returns a java.util.map object of the request parameter, with the parameter name as the Map key and the parameter value as the Map value.
     * the query string and the data from the POST request are aggregated into the set of request parameters. The query string data is sent before the POST data. For example,
     * if the request consists of the query string a= hello and the POST data a=goodbye&a=world, the resulting parameter set order will be =(hello,goodbye,world).
     * these apis do not expose the path parameters of GET requests (as defined in HTTP 1.1). They must be from the getRequestURI method or getPathInfo
     * is resolved in the string value returned by the.
     * <p>
     * the following conditions must be met before the POST form data is populated into the parameter set:
     * 1. The request is an HTTP or HTTPS request.
     * 2. The HTTP method is POST.
     * 3. Content type is application/x-www-form-urlencoded.
     * 4. The servlet has made an initial call to any getParameter method of the request object.
     * if these conditions are not met and POST form data is not included in the parameter set, the servlet must be able to pass the input of the request object
     * stream gets POST data. If these conditions are met, it is no longer valid to read the POST data directly from the input stream of the request object.
     */
    private void decodeBody() {
        //wait LastHttpContent
        try {
            inputStream.awaitDataIfNeed(-1);
        } catch (IOException e) {
            PlatformDependent.throwException(e);
        }

        if (postRequestDecoder == null) {
            return;
        }
        /*
         * There are three types of HttpDataType
         * Attribute, FileUpload, InternalAttribute
         */
        while (true) {
            InterfaceHttpData interfaceData;
            try {
                interfaceData = postRequestDecoder.next();
                if (interfaceData == null) {
                    return;
                }
            } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
                return;
            }

            InterfaceHttpData.HttpDataType httpDataType = interfaceData.getHttpDataType();
            if (httpDataType == InterfaceHttpData.HttpDataType.Attribute) {
                Attribute data = (Attribute) interfaceData;
                String name = data.getName();
                String value;
                try {
                    value = data.getValue();
                } catch (IOException e) {
                    value = "";
                }
                parameterMap.add(name, value);

                if (isMultipart) {
                    ServletTextPart part = new ServletTextPart(data, resourceManagerSupplier);
                    fileUploadList.add(part);
                }
            } else if (httpDataType == InterfaceHttpData.HttpDataType.FileUpload) {
                FileUpload data = (FileUpload) interfaceData;
                ServletFilePart part = new ServletFilePart(data, resourceManagerSupplier);
                fileUploadList.add(part);
            }
        }
    }

    /**
     * Parsing URL parameters
     */
    private void decodeUrlParameter() {
        if (decodePathsQueryIndex != -1) {
            Charset charset = Charset.forName(getCharacterEncoding());
            ServletUtil.decodeByUrl(parameterMap, nettyRequest.uri(), charset);
        }
        this.decodeParameterByUrlFlag = true;
    }

    /**
     * Parsing the cookie
     */
    private void decodeCookie() {
        if (cookieStringValue == null) {
            cookieStringValue = nettyRequest.headers().get(HttpHeaderConstants.COOKIE);
        }
        if (cookieStringValue != null && !cookieStringValue.isEmpty()) {
            this.cookies = ServletUtil.decodeCookie(cookieStringValue);
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
    private void decodeRemoteHost() {
        if (httpExchange.servletContext.enableLookupFlag) {
            InetSocketAddress inetSocketAddress = httpExchange.getRemoteAddress();
            if (inetSocketAddress == null) {
                throw new IllegalStateException("request invalid");
            }
            try {
                this.remoteHost = InetAddress.getByName(
                        inetSocketAddress.getHostName()).getHostName();
            } catch (IOException e) {
                //Ignore
            }
        } else {
            this.remoteHost = getRemoteAddr();
        }
    }

    /**
     * decode the host name of the server to which the request was sent.
     * It is the value of the part before ":" in the <code>Host</code>
     * header value, if any, or the resolved server name, or the server IP
     * address.
     */
    private void decodeServerNameAndPort() {
        String host = getHeader(HttpHeaderConstants.HOST.toString());
        StringBuilder sb;
        if (host != null && !host.isEmpty()) {
            sb = RecyclableUtil.newStringBuilder();
            int i = 0, length = host.length();
            boolean hasPort = false;
            while (i < length) {
                char c = host.charAt(i);
                if (c == ':') {
                    serverName = sb.toString();
                    sb.setLength(0);
                    hasPort = true;
                } else {
                    sb.append(c);
                }
                i++;
            }
            if (hasPort && sb.length() > 0) {
                serverPort = Integer.parseInt(sb.toString());
            } else {
                serverName = sb.toString();
                sb.setLength(0);
            }
        } else {
            serverName = getRemoteHost();
        }
        if (serverPort == 0) {
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
     * New session ID
     *
     * @return session ID
     */
    private String newSessionId() {
        return String.valueOf(SNOWFLAKE_ID_WORKER.nextId());
    }

    @Override
    public Cookie[] getCookies() {
        if (decodeCookieFlag) {
            return cookies;
        }
        decodeCookie();
        return cookies;
    }

    /**
     * servlet standard:
     * <p>
     * returns the value of the specified request header
     * is the long value, representing a
     * date object. Using this method
     * contains a header for the date, for example
     * Return date is
     * The number of milliseconds since January 1, 1970.
     * The first name is case insensitive.
     * , if the request does not have a header
     * specify a name, and this method returns -1. If the header Cannot convert to date,
     *
     * @param name ，Specifies the name of the title
     * @return Indicates the specified date in milliseconds as of January 1, 1970, or -1, if a title is specified. Not included in request
     * @throws IllegalArgumentException IllegalArgumentException
     */
    @Override
    public long getDateHeader(String name) throws IllegalArgumentException {
        String value = getHeader(name);
        if (value == null || value.isEmpty()) {
            return -1;
        }
        Date date = DateFormatter.parseHttpDate(value);
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
     *
     * @param name name
     * @return header value
     */
    @Override
    public String getHeader(String name) {
        Object value = nettyRequest.headers().get((CharSequence) name);
        return value == null ? null : value.toString();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set nameSet = nettyRequest.headers().names();
        return new Enumeration<String>() {
            private final Iterator iterator = nameSet.iterator();

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
     *
     * @return RequestURL
     */
    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0) {
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

    /**
     * PathInfo：Part of the request Path that is not part of the Context Path or Servlet Path. If there's no extra path, it's either null,
     * Or a string that starts with '/'.
     *
     * @return pathInfo
     */
    @Override
    public String getPathInfo() {
        if (this.pathInfo == null && dispatcher != null && dispatcher.path != null) {
            this.pathInfo = dispatcher.mapperElement.getPathInfo(dispatcher.path, decodePathsQueryIndex);
        }
        return this.pathInfo;
    }

    @Override
    public String getQueryString() {
        if (queryString == null) {
            if (decodePathsQueryIndex != -1) {
                queryString = nettyRequest.uri().substring(decodePathsQueryIndex + 1);
            }
        }
        return queryString;
    }

    private boolean existQueryStringKeyword(String name) {
        if (decodePathsQueryIndex == -1) {
            return false;
        } else {
            String uri = nettyRequest.uri();
            return uri.indexOf(name, decodePathsQueryIndex + 1) != -1;
        }
    }

    private boolean existCookieKeyword(String name) {
        if (cookieStringValue == null) {
            cookieStringValue = nettyRequest.headers().get(HttpHeaderConstants.COOKIE);
        }
        return cookieStringValue != null && cookieStringValue.contains(name);
    }

    @Override
    public String getRequestURI() {
        if (requestURI == null) {
            String uri = nettyRequest.uri();
            if (decodePathsQueryIndex != -1) {
                uri = uri.substring(0, decodePathsQueryIndex);
            }
            this.requestURI = uri;
        }
        return this.requestURI;
    }

    /**
     * Servlet Path: the Path section corresponds directly to the mapping of the activation request. The path starts with the "/" character, if the request is in the "/ *" or "" mode."
     * matches, in which case it is an empty string.
     *
     * @return servletPath
     */
    @Override
    public String getServletPath() {
        return dispatcher.mapperElement.getServletPath(dispatcher.relativePathNoQueryString);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Collection collection = nettyRequest.headers().getAll((CharSequence) name);
        return new Enumeration<String>() {
            private final Iterator iterator = collection.iterator();

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
     * <p>
     * returns the value of the specified request header
     * as int. If the request has no title
     * the name specified by this method returns -1. if This method does not convert headers to integers
     * throws a NumberFormatException code. The first name is case insensitive.
     *
     * @param name specifies the name of the request header
     * @return An integer request header representing a value or -1 if the request does not return -1 for the header with this name
     * @throws NumberFormatException If the header value cannot be converted to an int。
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
        return httpExchange.servletContext.contextPath;
    }

    private boolean isValidSessionId(String sessionId) {
        if (httpExchange == null) {
            return false;
        }
        ServletHttpSession httpSession = httpExchange.getHttpSession();
        if (httpSession != null && sessionId.equals(httpSession.getId())) {
            return httpSession.isValid();
        } else {
            ServletContext servletContext = httpExchange.servletContext;
            if (servletContext == null) {
                return false;
            }
            Session session = servletContext.getSessionService().getSession(sessionId);
            return session != null && session.isValid();
        }
    }

    @Override
    public ServletHttpSession getSession(boolean create) {
        // Scope in request cache. This can reduce multiple acquisitions.
        if (httpSession != null) {
            return httpSession;
        }

        // 1. find trace ID
        String sessionId = getRequestedSessionId0();

        // 2. fast return. If dont need create.
        boolean existSessionId = sessionId != null && !sessionId.isEmpty();
        if (!existSessionId && !create) {
            return null;
        }

        ServletContext servletContext;
        // 3. Scope in TCP connection. The session has already been created.
        ServletHttpSession httpSession = httpExchange.getHttpSession();
        if (existSessionId && httpSession != null && sessionId.equals(httpSession.getId()) && httpSession.isValid()) {
            this.httpSession = httpSession;
            httpSession.access();
            return httpSession;
        } else {
            servletContext = httpExchange.servletContext;
        }

        // 4. Scope in store. The session has already been created.
        Session session = this.session != null ? this.session : existSessionId ? servletContext.getSessionService().getSession(sessionId) : null;
        if (session == null) {
            if (create) {
                // 5. Create a new internal session.  It doesn't exist
                if (!existSessionId) {
                    this.sessionId = newSessionId();
                }
                session = new Session(this.sessionId, servletContext.sessionTimeout);
            } else {
                return null;
            }
        }
        // 6. Transformation decorate HttpSession
        httpSession = new ServletHttpSession(session, servletContext);
        httpSession.access();

        // 7. bind to current request cache.
        this.httpSession = httpSession;
        // 8. bind to current TCP connection.
        httpExchange.setHttpSession(httpSession);
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
        ServletContext servletContext = httpExchange.servletContext;

        servletContext.getSessionService().changeSessionId(oldSessionId, newSessionId);

        sessionId = newSessionId;
        httpSession.setId(sessionId);

        ServletEventListenerManager listenerManager = servletContext.servletEventListenerManager;
        if (listenerManager.hasHttpSessionIdListener()) {
            listenerManager.onHttpSessionIdChanged(new HttpSessionEvent(httpSession), oldSessionId);
        }
        return newSessionId;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        String requestedSessionId = getRequestedSessionId0();
        if (requestedSessionId == null || requestedSessionId.isEmpty()) {
            return false;
        }
        return isValidSessionId(requestedSessionId);
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        getRequestedSessionId0();
        return sessionIdSource == SessionTrackingMode.COOKIE;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return isRequestedSessionIdFromUrl();
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        getRequestedSessionId0();
        return sessionIdSource == SessionTrackingMode.URL;
    }

    String getRequestedSessionId0() {
        if (getRequestedSessionId0) {
            return sessionId;
        }

        //If the user sets the sessionCookie name, the user set the sessionCookie name
        ServletContext servletContext = httpExchange.servletContext;
        Set<SessionTrackingMode> sessionTrackingModes = servletContext.getEffectiveSessionTrackingModes();
        String sessionId = null;
        SessionTrackingMode sessionIdSource = null;
        // cookie
        if (sessionTrackingModes.contains(SessionTrackingMode.COOKIE)) {
            String cookieSessionName = servletContext.getSessionCookieParamName();
            //Find the value of sessionCookie first from cookie, then from url parameter
            if (existCookieKeyword(cookieSessionName)) {
                Cookie[] cookies = getCookies();
                if (cookies != null) {
                    String lastCookieValue = null;
                    for (Cookie cookie : cookies) {
                        String cookieName = cookie.getName();
                        if (!cookieSessionName.equals(cookieName)) {
                            continue;
                        }
                        String cookieValue = cookie.getValue();
                        if (cookieValue == null || cookieValue.isEmpty()) {
                            continue;
                        }
                        Session session = servletContext.getSessionService().getSession(cookieValue);
                        if (session != null && session.isValid()) {
                            sessionId = cookieValue;
                            sessionIdSource = SessionTrackingMode.COOKIE;
                            lastCookieValue = null;
                            this.session = session;
                            break;
                        } else {
                            // 替换会话id，直到有效为止
                            lastCookieValue = cookieValue;
                        }
                    }
                    if (lastCookieValue != null) {
                        sessionId = lastCookieValue;
                        sessionIdSource = SessionTrackingMode.COOKIE;
                    }
                }
            }
        }
        // url
        if (sessionIdSource == null && sessionTrackingModes.contains(SessionTrackingMode.URL)) {
            String sessionUriParamName = servletContext.getSessionUriParamName();
            if (existQueryStringKeyword(sessionUriParamName)) {
                sessionId = getParameter(sessionUriParamName);
            }
            if (sessionId != null && !sessionId.isEmpty()) {
                sessionIdSource = SessionTrackingMode.URL;
            }
        }
        // ssl only
        if (sessionIdSource == null && sessionTrackingModes.size() == 1 && sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
            ServletHttpSession httpSession = httpExchange.getHttpSession();
            if (httpSession != null) {
                sessionId = httpSession.getId();
                this.httpSession = httpSession;
            } else {
                ChannelId id = httpExchange.channelHandlerContext.channel().id();
                sessionId = id.asLongText();
            }
            sessionIdSource = SessionTrackingMode.SSL;
        }
        this.sessionIdSource = sessionIdSource;
        this.sessionId = sessionId;
        this.getRequestedSessionId0 = true;
        return sessionId;
    }

    @Override
    public String getRequestedSessionId() {
        String sessionId = getRequestedSessionId0();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = newSessionId();
        }
        this.sessionId = sessionId;
        return sessionId;
    }

    @Override
    public Object getAttribute(String name) {
        return attributeMap.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributeMap.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            String characterEncoding = ServletUtil.decodeCharacterEncoding(getContentType());
            if (characterEncoding == null) {
                characterEncoding = httpExchange.servletContext.requestCharacterEncoding;
            }
            this.characterEncoding = characterEncoding;
        }
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        characterEncoding = env;
    }

    @Override
    public int getContentLength() {
        return (int) inputStream.contentLength;
    }

    @Override
    public long getContentLengthLong() {
        return inputStream.contentLength;
    }

    @Override
    public String getContentType() {
        Object value = nettyRequest.headers().get(HttpHeaderConstants.CONTENT_TYPE);
        return value == null ? null : value.toString();
    }

    @Override
    public ServletInputStreamWrapper getInputStream() {
        if (reader != null) {
            throw new IllegalStateException("getReader() has already been called for this request");
        }
        usingInputStreamFlag = true;
        return inputStream;
    }

    @Override
    public String getParameter(String name) {
        String[] values;
        if (httpExchange.servletContext.notExistBodyParameters.contains(name)) {
            if (!decodeParameterByUrlFlag) {
                decodeUrlParameter();
            }
            values = unmodifiableParameterMap.get(name);
        } else {
            values = getParameterMap().get(name);
        }
        if (values == null || values.length == 0) {
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
        if (!decodeParameterByUrlFlag) {
            decodeUrlParameter();
        }
        if (inputStream.existBody()) {
            if (decodeBodyFlag.compareAndSet(false, true)) {
                decodeBody();
            } else {
                try {
                    inputStream.awaitDataIfNeed(-1);
                } catch (IOException e) {
                    PlatformDependent.throwException(e);
                }
            }
        }
        return unmodifiableParameterMap;
    }

    @Override
    public String getProtocol() {
        Protocol protocol = httpExchange.getProtocol();
        if (protocol.isHttp2()) {
            return "HTTP/2.0";
        } else {
            return nettyRequest.protocolVersion().toString();
        }
    }

    boolean isSupportsRelativeRedirects() {
        return nettyRequest.protocolVersion() != HttpVersion.HTTP_1_0;
    }

    @Override
    public String getScheme() {
        if (scheme == null) {
            decodeScheme();
        }
        return scheme;
    }

    @Override
    public String getServerName() {
        if (serverName == null) {
            decodeServerNameAndPort();
        }
        return serverName;
    }

    @Override
    public int getServerPort() {
        if (serverPort == 0) {
            decodeServerNameAndPort();
        }
        return serverPort;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (usingInputStreamFlag) {
            throw new IllegalStateException("getInputStream() has already been called for this request");
        }
        if (reader == null) {
            synchronized (this) {
                if (reader == null) {
                    String charset = getCharacterEncoding();
                    if (charset == null) {
                        charset = httpExchange.servletContext.getRequestCharacterEncoding();
                    }
                    reader = new BufferedReader(new InputStreamReader(inputStream, charset));
                }
            }
        }
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress inetSocketAddress = httpExchange.getRemoteAddress();
        if (inetSocketAddress == null) {
            return null;
        }
        InetAddress inetAddress = inetSocketAddress.getAddress();
        if (inetAddress == null) {
            return null;
        }
        return inetAddress.getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        if (remoteHost == null) {
            decodeRemoteHost();
        }
        return remoteHost;
    }

    @Override
    public int getRemotePort() {
        InetSocketAddress inetSocketAddress = httpExchange.getRemoteAddress();
        if (inetSocketAddress == null) {
            return 0;
        }
        return inetSocketAddress.getPort();
    }

    @Override
    public void setAttribute(String name, Object object) {
        Objects.requireNonNull(name);

        if (object == null) {
            removeAttribute(name);
            return;
        }

        Object oldObject = attributeMap.put(name, object);

        ServletContext servletContext = httpExchange.servletContext;
        ServletEventListenerManager listenerManager = servletContext.servletEventListenerManager;
        if (listenerManager.hasServletRequestAttributeListener()) {
            listenerManager.onServletRequestAttributeAdded(new ServletRequestAttributeEvent(servletContext, this, name, object));
            if (oldObject != null) {
                listenerManager.onServletRequestAttributeReplaced(new ServletRequestAttributeEvent(servletContext, this, name, oldObject));
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object oldObject = attributeMap.remove(name);

        ServletContext servletContext = httpExchange.servletContext;
        ServletEventListenerManager listenerManager = servletContext.servletEventListenerManager;
        if (listenerManager.hasServletRequestAttributeListener()) {
            listenerManager.onServletRequestAttributeRemoved(new ServletRequestAttributeEvent(servletContext, this, name, oldObject));
        }
    }

    @Override
    public Locale getLocale() {
        if (this.locales == null) {
            decodeLocale();
        }

        Locale[] locales = this.locales;
        if (locales == null || locales.length == 0) {
            return null;
        }
        return locales[0];
    }

    @Override
    public Enumeration<Locale> getLocales() {
        if (this.locales == null) {
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
        return httpExchange.servletContext.getRequestDispatcher(path, getDispatcherType(), true);
    }

    @Override
    public String getRealPath(String path) {
        return httpExchange.servletContext.getRealPath(path);
    }

    @Override
    public String getLocalName() {
        return httpExchange.servletContext.serverAddress.getHostName();
    }

    @Override
    public String getLocalAddr() {
        return httpExchange.servletContext.serverAddress.getAddress().getHostAddress();
    }

    @Override
    public int getLocalPort() {
        return httpExchange.servletContext.serverAddress.getPort();
    }

    @Override
    public ServletContext getServletContext() {
        return httpExchange.servletContext;
    }

    @Override
    public ServletAsyncContext startAsync() throws IllegalStateException {
        return startAsync(this, httpExchange.response);
    }

    @Override
    public ServletAsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        if (!isAsyncSupported()) {
            throw new IllegalStateException("Asynchronous is not supported");
        }

        ServletContext servletContext = httpExchange.servletContext;
        if (asyncContext == null) {
            asyncContext = new ServletAsyncContext(httpExchange, servletContext, servletContext.getExecutor(), servletContext.getAsyncTimeout());
        }
        asyncContext.servletRequest = servletRequest;
        asyncContext.servletResponse = servletResponse;
        asyncContext.setStart();
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
        if (dispatcherType == null) {
            return DispatcherType.REQUEST;
        }
        return this.dispatcherType;
    }

    void setDispatcherType(DispatcherType dispatcherType) {
        this.dispatcherType = dispatcherType;
    }

    @Override
    public String getPathTranslated() {
        ServletContext servletContext = httpExchange.servletContext;
        String contextPath = servletContext.contextPath;
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
     *
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
        if (principal != null) {
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
        if (!inputStream.existBody()) {
            return fileUploadList;
        }
        if (decodeBodyFlag.compareAndSet(false, true)) {
            try {
                decodeBody();
            } catch (CodecException e) {
                Throwable cause = getCause(e);
                setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpServletResponse.SC_BAD_REQUEST);
                if (cause instanceof IOException) {
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION, cause);
                    throw (IOException) cause;
                } else if (cause instanceof IllegalStateException) {
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION, cause);
                    throw (IllegalStateException) cause;
                } else if (cause instanceof IllegalArgumentException) {
                    IllegalStateException illegalStateException = new IllegalStateException("HttpServletRequest.getParts() -> decodeFile() fail : " + cause.getMessage(), cause);
                    illegalStateException.setStackTrace(cause.getStackTrace());
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION, illegalStateException);
                    throw illegalStateException;
                } else {
                    ServletException servletException;
                    if (cause != null) {
                        servletException = new ServletException("HttpServletRequest.getParts() -> decodeFile() fail : " + cause.getMessage(), cause);
                        servletException.setStackTrace(cause.getStackTrace());
                    } else {
                        servletException = new ServletException("HttpServletRequest.getParts() -> decodeFile() fail : " + e.getMessage(), e);
                        servletException.setStackTrace(e.getStackTrace());
                    }
                    setAttribute(RequestDispatcher.ERROR_EXCEPTION, servletException);
                    throw servletException;
                }
            } catch (IllegalArgumentException e) {
                IllegalStateException illegalStateException = new IllegalStateException("HttpServletRequest.getParts() -> decodeFile() fail : " + e.getMessage(), e);
                illegalStateException.setStackTrace(e.getStackTrace());
                setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpServletResponse.SC_BAD_REQUEST);
                setAttribute(RequestDispatcher.ERROR_EXCEPTION, illegalStateException);
                throw illegalStateException;
            }
        } else {
            inputStream.awaitDataIfNeed(-1);
        }
        return fileUploadList;
    }

    private Throwable getCause(Throwable throwable) {
        if (throwable.getCause() == null) {
            return null;
        }
        while (true) {
            Throwable cause = throwable;
            throwable = throwable.getCause();
            if (throwable == null) {
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
            throw new ServletException(e.getMessage(), e);
        }
    }

    @Override
    public void recycle() {
        ServletHttpSession httpSession = this.httpSession;
        if (httpSession != null) {
            httpSession.save();
        }
        this.inputStream.recycle();

        if (!fileUploadList.isEmpty()) {
            Part[] parts = fileUploadList.toArray(new Part[fileUploadList.size()]);
            ServletContext.asyncClose(() -> {
                for (Part part : parts) {
                    try {
                        part.delete();
                    } catch (IOException ignored) {
                    }
                }
            });
        }
        InterfaceHttpPostRequestDecoder postRequestDecoder = this.postRequestDecoder;
        ServletContext.asyncClose(() -> {
            if (postRequestDecoder != null) {
                try {
                    postRequestDecoder.destroy();
                } catch (IllegalStateException ignored) {

                }
            }
        });
        this.postRequestDecoder = null;

        if (httpExchange.isAbort()) {
            return;
        }
        this.cookieStringValue = null;
        this.httpSession = null;
        this.nettyRequest = null;
        this.decodeBodyFlag.set(false);
        this.decodeParameterByUrlFlag = false;
        this.remoteSchemeFlag = false;
        this.decodeCookieFlag = false;
        this.usingInputStreamFlag = false;
        this.reader = null;
        this.sessionIdSource = null;
        this.remoteHost = null;
        this.serverName = null;
        this.serverPort = 0;
        this.scheme = null;
        this.queryString = null;
        this.getRequestedSessionId0 = false;
        this.session = null;
        this.pathInfo = null;
        this.requestURI = null;
        this.characterEncoding = null;
        this.sessionId = null;
        this.cookies = null;
        this.locales = null;
        this.asyncContext = null;
        this.httpExchange = null;
        this.multipartConfigElement = null;
        this.servletSecurityElement = null;
        this.dispatcherType = null;
        this.dispatcher = null;

        this.parameterMap.clear();
        this.fileUploadList.clear();
        this.attributeMap.clear();
        RECYCLER.recycleInstance(this);
    }

}
