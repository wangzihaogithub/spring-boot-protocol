package com.github.netty.protocol.servlet;

import com.github.netty.Version;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.ResourceManager;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.protocol.servlet.util.*;
import com.github.netty.protocol.servlet.websocket.WebSocketServerContainer;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.FastThreadLocal;

import javax.servlet.*;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Servlet context (lifetime same as server)
 *
 * @author wangzihao
 * 2018/7/14/014
 */
public class ServletContext implements javax.servlet.ServletContext {

    public static final int MIN_FILE_SIZE_THRESHOLD = 16384;
    public static final String DEFAULT_UPLOAD_DIR = "/upload";
    public static final String SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE = "javax.websocket.server.ServerContainer";
    private static final boolean SUPPORT_SET_BASE_DIR;
    private static final List<ServletContext> INSTANCE_LIST = Collections.synchronizedList(new ArrayList<>(2));

    static {
        boolean supportSetBaseDir;
        try {
            DefaultHttpDataFactory.class.getDeclaredMethod("setBaseDir", String.class);
            supportSetBaseDir = true;
        } catch (Throwable e) {
            supportSetBaseDir = false;
        }
        SUPPORT_SET_BASE_DIR = supportSetBaseDir;
    }

    //    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    final ServletErrorPageManager servletErrorPageManager = new ServletErrorPageManager();
    /**
     * Will not appear in the field in http body. multipart/form-data, application/x-www-form-urlencoded. （In order to avoid the client, you have been waiting for the client.）
     */
    final Collection<String> notExistBodyParameters = new HashSet<>(Arrays.asList("_method", HttpConstants.JSESSION_ID_COOKIE));
    final ServletEventListenerManager servletEventListenerManager = new ServletEventListenerManager();
    final ServletSessionCookieConfig sessionCookieConfig = new ServletSessionCookieConfig();
    private final Map<String, Object> attributeMap = new LinkedHashMap<>(16);
    private final Map<String, String> initParamMap = new LinkedHashMap<>(16);
    private final Map<String, ServletRegistration> servletRegistrationMap = new LinkedHashMap<>(8);
    private final Map<String, ServletFilterRegistration> filterRegistrationMap = new LinkedHashMap<>(8);
    private final FastThreadLocal<Map<Charset, DefaultHttpDataFactory>> httpDataFactoryThreadLocal = new FastThreadLocal<Map<Charset, DefaultHttpDataFactory>>() {
        @Override
        protected Map<Charset, DefaultHttpDataFactory> initialValue() throws Exception {
            return new LinkedHashMap<>(3);
        }
    };
    private final Set<SessionTrackingMode> defaultSessionTrackingModeSet = new HashSet<>(Arrays.asList(SessionTrackingMode.COOKIE, SessionTrackingMode.URL));
    private final MimeMappingsX mimeMappings = new MimeMappingsX();
    private final UrlMapper<ServletRegistration> servletUrlMapper = new UrlMapper<>();
    private final FilterMapper<ServletFilterRegistration> filterUrlMapper = new FilterMapper<>();
    private final ClassLoader classLoader;
    private LoggerX logger = LoggerFactoryX.getLogger(getLogName(""));
    Supplier<Executor> defaultExecutorSupplier;
    String contextPath = "";
    /**
     * Default: 20 minutes,
     */
    int sessionTimeout = 1200;
    ResourceManager resourceManager;
    boolean autoFlush;
    CharSequence serverHeaderAscii;
    InetSocketAddress serverAddress;
    /**
     * Minimum upload file length, in bytes (becomes temporary file storage if larger than uploadMinSize)
     */
    int fileSizeThreshold = 4096 * 16;
    /**
     * Upload file timeout millisecond , -1 is not control timeout.
     */
    long uploadFileTimeoutMs = -1;
    /**
     * 客户端断开后，如果超过abortAfterMessageTimeoutMs后，还没有收到完整的body，则抛出abort异常
     */
    long abortAfterMessageTimeoutMs = 500;
    private Supplier<Executor> asyncExecutorSupplier;
    private SessionService sessionService;
    private Set<SessionTrackingMode> sessionTrackingModeSet;
    private Servlet defaultServlet = new DefaultServlet();
    boolean enableLookupFlag = false;
    private boolean mapperContextRootRedirectEnabled = true;
    boolean useRelativeRedirects = true;
    private String serverHeader;
    String requestCharacterEncoding = HttpConstants.DEFAULT_CHARSET.name();
    Charset requestCharacterEncodingCharset = HttpConstants.DEFAULT_CHARSET;
    String responseCharacterEncoding = HttpConstants.DEFAULT_CHARSET.name();
    Charset responseCharacterEncodingCharset = HttpConstants.DEFAULT_CHARSET;
    private String servletContextName;
    /**
     * output stream maxBufferBytes
     * Each buffer accumulate the maximum number of bytes (default 1M)
     */
    private int maxBufferBytes = 1024 * 1024;

    public ServletContext() {
        this(null);
    }

    public ServletContext(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? getClass().getClassLoader() : classLoader;
        INSTANCE_LIST.add(this);
    }

    public static void asyncClose(Closeable closeable) {
        Executor executor = null;
        for (ServletContext servletContext : INSTANCE_LIST) {
            executor = servletContext.asyncExecutorSupplier != null ? servletContext.asyncExecutorSupplier.get() : null;
            if (executor == null) {
                executor = servletContext.defaultExecutorSupplier.get();
            }
            if (executor != null) {
                break;
            }
        }
        if (executor != null) {
            executor.execute(() -> {
                try {
                    closeable.close();
                } catch (IOException ignored) {

                }
            });
        } else {
            try {
                closeable.close();
            } catch (IOException ignored) {

            }
        }
    }

    private static String getLogName(String name) {
        if ((name == null) || (name.isEmpty())) {
            name = "/";
        } else if (name.startsWith("##")) {
            name = "/" + name;
        }
        return "[" + name + "]";
    }

    public static String normPath(String path) {
        if (path.isEmpty()) {
            return path;
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1) {
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        }
        return path;
    }

    /**
     * 是否开启UrlServlet的AntPathMatcher路径匹配,默认false不开启
     */
    public void setEnableUrlServletAntPathMatcher(boolean enableAntPathMatcher) {
        servletUrlMapper.setEnableAntPathMatcher(enableAntPathMatcher);
    }

    /**
     * 是否开启UrlFilter的AntPathMatcher路径匹配,默认false不开启
     */
    public void setEnableUrlFilterAntPathMatcher(boolean enableAntPathMatcher) {
        filterUrlMapper.setEnableAntPathMatcher(enableAntPathMatcher);
    }

    public boolean isEnableUrlServletAntPathMatcher() {
        return servletUrlMapper.isEnableAntPathMatcher();
    }

    public boolean isEnableUrlFilterAntPathMatcher() {
        return filterUrlMapper.isEnableAntPathMatcher();
    }

    public DefaultServlet getDefaultServletCast() {
        if (defaultServlet instanceof DefaultServlet) {
            return (DefaultServlet) defaultServlet;
        } else {
            return null;
        }
    }

    public Servlet getDefaultServlet() {
        return defaultServlet;
    }

    public void setDefaultServlet(Servlet defaultServlet) {
        this.defaultServlet = defaultServlet;
    }

    public int getMaxBufferBytes() {
        return maxBufferBytes;
    }

    public void setMaxBufferBytes(int maxBufferBytes) {
        this.maxBufferBytes = maxBufferBytes;
    }

    public boolean isAutoFlush() {
        return autoFlush;
    }

    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }

    public long getUploadFileTimeoutMs() {
        return uploadFileTimeoutMs;
    }

    public void setUploadFileTimeoutMs(long uploadFileTimeoutMs) {
        this.uploadFileTimeoutMs = uploadFileTimeoutMs;
    }

    public void setAbortAfterMessageTimeoutMs(long abortAfterMessageTimeoutMs) {
        this.abortAfterMessageTimeoutMs = abortAfterMessageTimeoutMs;
    }

    public long getAbortAfterMessageTimeoutMs() {
        return abortAfterMessageTimeoutMs;
    }

    public boolean isUseRelativeRedirects() {
        return useRelativeRedirects;
    }

    public void setUseRelativeRedirects(boolean useRelativeRedirects) {
        this.useRelativeRedirects = useRelativeRedirects;
    }

    public boolean isMapperContextRootRedirectEnabled() {
        return mapperContextRootRedirectEnabled;
    }

    public void setMapperContextRootRedirectEnabled(boolean mapperContextRootRedirectEnabled) {
        this.mapperContextRootRedirectEnabled = mapperContextRootRedirectEnabled;
    }

    public boolean isEnableLookupFlag() {
        return enableLookupFlag;
    }

    public void setEnableLookupFlag(boolean enableLookupFlag) {
        this.enableLookupFlag = enableLookupFlag;
    }

    public void setDocBase(String docBase) {
        setDocBase(docBase, "/localhost");
    }

    public void setDocBase(String docBase, String workspace) {
        ResourceManager old = this.resourceManager;
        this.resourceManager = new ResourceManager(docBase, workspace, classLoader);
        if (old != null) {
            logger.warn("ServletContext docBase override. old = {}, new = {}", old, this.resourceManager);
        }
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = resourceManager.getRealPath(DEFAULT_UPLOAD_DIR);
        DiskAttribute.baseDirectory = resourceManager.getRealPath(DEFAULT_UPLOAD_DIR);
    }

    public Executor getExecutor() {
        Executor executor = asyncExecutorSupplier != null ? asyncExecutorSupplier.get() : null;
        if (executor == null) {
            executor = defaultExecutorSupplier.get();
        }
        if (executor == null) {
            throw new IllegalStateException("no found async Executor");
        }
        return executor;
    }

    public Executor getAsyncExecutor() {
        return asyncExecutorSupplier != null ? asyncExecutorSupplier.get() : null;
    }

    public Collection<String> getNotExistBodyParameters() {
        return notExistBodyParameters;
    }

    public void setAsyncExecutorSupplier(Supplier<Executor> asyncExecutorSupplier) {
        this.asyncExecutorSupplier = asyncExecutorSupplier;
    }

    public Supplier<Executor> getDefaultExecutorSupplier() {
        return defaultExecutorSupplier;
    }

    public void setDefaultExecutorSupplier(Supplier<Executor> defaultExecutorSupplier) {
        this.defaultExecutorSupplier = defaultExecutorSupplier;
    }

    public HttpDataFactory getHttpDataFactory(Charset charset) {
        Map<Charset, DefaultHttpDataFactory> httpDataFactoryMap = httpDataFactoryThreadLocal.get();
        return httpDataFactoryMap.computeIfAbsent(charset, c -> {
            DefaultHttpDataFactory factory = new DefaultHttpDataFactory(fileSizeThreshold, c);
            if (SUPPORT_SET_BASE_DIR) {
                factory.setDeleteOnExit(true);
                factory.setBaseDir(resourceManager.mkdirs(DEFAULT_UPLOAD_DIR).toString());
            }
            return factory;
        });
    }

    public int getFileSizeThreshold() {
        return fileSizeThreshold;
    }

    public void setFileSizeThreshold(long fileSizeThreshold) {
        this.fileSizeThreshold = (int) Math.max(fileSizeThreshold, MIN_FILE_SIZE_THRESHOLD);
    }

    public MimeMappingsX getMimeMappings() {
        return mimeMappings;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public ServletErrorPageManager getErrorPageManager() {
        return servletErrorPageManager;
    }

    public String getServerHeader() {
        return serverHeader;
    }

    public void setServerHeader(String serverHeader) {
        this.serverHeader = serverHeader;
        if (serverHeader != null) {
            this.serverHeaderAscii = AsciiString.cached(serverHeader);
        }
    }

    public ServletEventListenerManager getServletEventListenerManager() {
        return servletEventListenerManager;
    }

    public long getAsyncTimeout() {
        String value = getInitParameter("asyncTimeout");
        if (value == null || value.isEmpty()) {
            return 30000;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 30000;
        }
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(InetSocketAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public SessionService getSessionService() {
        if (sessionService == null) {
            synchronized (this) {
                if (sessionService == null) {
                    sessionService = new SessionLocalMemoryServiceImpl(this);
                }
            }
        }
        return sessionService;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        if (sessionTimeout <= 0) {
            return;
        }
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        String normed = normPath(contextPath);
        this.contextPath = normed;
        this.filterUrlMapper.setRootPath(normed);
        this.servletUrlMapper.setRootPath(normed);
        this.logger = LoggerFactoryX.getLogger(getLogName(normed));
    }

    @Override
    public ServletContext getContext(String uripath) {
        if ("/".equals(uripath)) {
            return this;
        }
        return null;
    }

    @Override
    public int getMajorVersion() {
        return 4;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return 3;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return 0;
    }

    @Override
    public String getMimeType(String file) {
        if (file == null) {
            return null;
        }
        int period = file.lastIndexOf('.');
        if (period < 0) {
            return null;
        }
        String extension = file.substring(period + 1);
        if (extension.isEmpty()) {
            return null;
        }
        return mimeMappings.get(extension);
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        return resourceManager.getResourcePaths(path);
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
        return resourceManager.getResource(path);
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        return resourceManager.getResourceAsStream(path);
    }

    @Override
    public String getRealPath(String path) {
        return resourceManager.getRealPath(path);
    }

    @Override
    public ServletRequestDispatcher getRequestDispatcher(String path) {
        return getRequestDispatcher(path, DispatcherType.REQUEST, true);
    }

    ServletRequestDispatcher getRequestDispatcher(String path, DispatcherType dispatcherType, boolean normalize) {
        String pathNormalize = normalize ? ServletUtil.pathNormalize(path, true) : path;
        if (pathNormalize == null) {
            return null;
        }
        int queryIndex = pathNormalize.indexOf('?');
        String relativePathNoQueryString = queryIndex != -1 ? pathNormalize.substring(0, queryIndex) : pathNormalize;
        UrlMapper.Element<ServletRegistration> element = servletUrlMapper.getMappingObjectByServletPath(relativePathNoQueryString);
        if (element == null) {
            return null;
        }
        ServletRegistration servletRegistration = element.getObject();
        if (servletRegistration == null) {
            return null;
        }
        ServletFilterChain filterChain = ServletFilterChain.newInstance(this, servletRegistration);
        filterUrlMapper.addMappingObjects(relativePathNoQueryString, dispatcherType, filterChain.filterRegistrationList);
        return ServletRequestDispatcher.newInstancePath(filterChain, pathNormalize, contextPath, relativePathNoQueryString, element, queryIndex);
    }

    @Override
    public ServletRequestDispatcher getNamedDispatcher(String name) {
        ServletRegistration servletRegistration = null == name ? null : getServletRegistration(name);
        if (servletRegistration == null) {
            return null;
        }

        ServletFilterChain filterChain = ServletFilterChain.newInstance(this, servletRegistration);
        List<FilterMapper.Element<ServletFilterRegistration>> filterList = filterChain.filterRegistrationList;
        for (ServletFilterRegistration registration : filterRegistrationMap.values()) {
            for (String servletName : registration.servletNameMappingSet) {
                if (servletName.equals(name)) {
                    filterList.add(new FilterMapper.Element<>(name, registration));
                }
            }
        }
        return ServletRequestDispatcher.newInstanceName(filterChain, name, contextPath);
    }

    @Override
    public Servlet getServlet(String name) throws ServletException {
        ServletRegistration registration = servletRegistrationMap.get(name);
        if (registration == null) {
            return null;
        }
        return registration.getServlet();
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        List<Servlet> list = new ArrayList<>();
        for (ServletRegistration registration : servletRegistrationMap.values()) {
            list.add(registration.getServlet());
        }
        return Collections.enumeration(list);
    }

    @Override
    public Enumeration<String> getServletNames() {
        List<String> list = new ArrayList<>();
        for (ServletRegistration registration : servletRegistrationMap.values()) {
            list.add(registration.getName());
        }
        return Collections.enumeration(list);
    }

    @Override
    public void log(String msg) {
        if (logger.isInfoEnabled()) {
            logger.info(msg);
        }
    }

    @Override
    public void log(Exception exception, String msg) {
        if (logger.isErrorEnabled()) {
            logger.error(msg, exception);
        }
    }

    @Override
    public void log(String message, Throwable throwable) {
        if (logger.isErrorEnabled()) {
            logger.error(message, throwable);
        }
    }

    @Override
    public String getServerInfo() {
        return Version.getServerInfo()
                + ("(JDK ")
                + (Version.getJvmVersion())
                + (";")
                + (Version.getOsName())
                + (" ")
                + (Version.getArch())
                + (")");
    }

    @Override
    public String getInitParameter(String name) {
        return initParamMap.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParamMap.keySet());
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        return initParamMap.putIfAbsent(name, value) == null;
    }

    @Override
    public Object getAttribute(String name) {
        if (SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE.equals(name)) {
            try {
                attributeMap.put(name, new WebSocketServerContainer());
            } catch (Exception ignored) {
            }
        }
        return attributeMap.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributeMap.keySet());
    }

    @Override
    public void setAttribute(String name, Object object) {
        Objects.requireNonNull(name);
        if (object == null) {
            removeAttribute(name);
            return;
        }

        Object oldObject = attributeMap.put(name, object);
        ServletEventListenerManager listenerManager = this.servletEventListenerManager;
        if (listenerManager.hasServletContextAttributeListener()) {
            listenerManager.onServletContextAttributeAdded(new ServletContextAttributeEvent(this, name, object));
            if (oldObject != null) {
                listenerManager.onServletContextAttributeReplaced(new ServletContextAttributeEvent(this, name, oldObject));
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object oldObject = attributeMap.remove(name);
        ServletEventListenerManager listenerManager = this.servletEventListenerManager;
        if (listenerManager.hasServletContextAttributeListener()) {
            listenerManager.onServletContextAttributeRemoved(new ServletContextAttributeEvent(this, name, oldObject));
        }
    }

    @Override
    public String getServletContextName() {
        return servletContextName;
    }

    public void setServletContextName(String servletContextName) {
        this.servletContextName = servletContextName;
    }

    @Override
    public ServletRegistration addServlet(String servletName, String className) {
        try {
            return addServlet(servletName, (Class<? extends Servlet>) Class.forName(className).newInstance());
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("addServlet error =" + e + ",servletName=" + servletName, e);
        }
    }

    @Override
    public ServletRegistration addServlet(String servletName, Servlet servlet) {
        Servlet newServlet = servletEventListenerManager.onServletAdded(servlet);
        ServletRegistration servletRegistration = new ServletRegistration(servletName, newServlet != null ? newServlet : servlet, this, servletUrlMapper);
        servletRegistrationMap.put(servletName, servletRegistration);
        return servletRegistration;
    }

    @Override
    public ServletRegistration addServlet(String servletName, Class<? extends Servlet> servletClass) {
        Servlet servlet = null;
        try {
            servlet = servletClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new IllegalStateException("createServlet error =" + e + ",servletName=" + servletName, e);
        }
        return addServlet(servletName, servlet);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        try {
            return clazz.getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new ServletException("createServlet error =" + e + ",clazz=" + clazz, e);
        }
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return servletRegistrationMap.get(servletName);
    }

    @Override
    public Map<String, ServletRegistration> getServletRegistrations() {
        return servletRegistrationMap;
    }

    @Override
    public ServletFilterRegistration addFilter(String filterName, String className) {
        try {
            return addFilter(filterName, (Class<? extends Filter>) Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("addFilter error =" + e + ",filterName=" + filterName, e);
        }
    }

    @Override
    public ServletFilterRegistration addFilter(String filterName, Filter filter) {
        ServletFilterRegistration registration = new ServletFilterRegistration(filterName, filter, this, filterUrlMapper);
        filterRegistrationMap.put(filterName, registration);
        return registration;
    }

    @Override
    public ServletFilterRegistration addFilter(String filterName, Class<? extends Filter> filterClass) {
        try {
            return addFilter(filterName, filterClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("addFilter error =" + e, e);
        }
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ServletException("createFilter error =" + e, e);
        }
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return filterRegistrationMap.get(filterName);
    }

    @Override
    public Map<String, ServletFilterRegistration> getFilterRegistrations() {
        return filterRegistrationMap;
    }

    @Override
    public ServletSessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }

    public String getSessionCookieParamName() {
        String userSettingCookieName = sessionCookieConfig.getName();
        return userSettingCookieName != null && !userSettingCookieName.isEmpty() ?
                userSettingCookieName : HttpConstants.JSESSION_ID_COOKIE;
    }

    public String getSessionUriParamName() {
        String userSettingCookieName = sessionCookieConfig.getName();
        if (userSettingCookieName == null || userSettingCookieName.isEmpty()) {
            userSettingCookieName = HttpConstants.JSESSION_ID_URL;
        }
        return userSettingCookieName;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        sessionTrackingModeSet = sessionTrackingModes;
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return defaultSessionTrackingModeSet;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        if (sessionTrackingModeSet == null) {
            return defaultSessionTrackingModeSet;
        }
        return sessionTrackingModeSet;
    }

    @Override
    public void addListener(String className) {
        try {
            addListener((Class<? extends EventListener>) Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("addListener error =" + e + ",className=" + className, e);
        }
    }

    @Override
    public <T extends EventListener> void addListener(T listener) {
        Objects.requireNonNull(listener);

        boolean addFlag = false;
        ServletEventListenerManager listenerManager = this.servletEventListenerManager;
        if (listener instanceof ServletContextAttributeListener) {
            listenerManager.addServletContextAttributeListener((ServletContextAttributeListener) listener);
            addFlag = true;
        }
        if (listener instanceof ServletRequestListener) {
            listenerManager.addServletRequestListener((ServletRequestListener) listener);
            addFlag = true;
        }
        if (listener instanceof ServletRequestAttributeListener) {
            listenerManager.addServletRequestAttributeListener((ServletRequestAttributeListener) listener);
            addFlag = true;
        }
        if (listener instanceof HttpSessionIdListener) {
            listenerManager.addHttpSessionIdListenerListener((HttpSessionIdListener) listener);
            addFlag = true;
        }
        if (listener instanceof HttpSessionAttributeListener) {
            listenerManager.addHttpSessionAttributeListener((HttpSessionAttributeListener) listener);
            addFlag = true;
        }
        if (listener instanceof HttpSessionListener) {
            listenerManager.addHttpSessionListener((HttpSessionListener) listener);
            addFlag = true;
        }
        if (listener instanceof ServletContextListener) {
            listenerManager.addServletContextListener((ServletContextListener) listener);
            addFlag = true;
        }
        if (!addFlag) {
            throw new IllegalArgumentException("applicationContext.addListener.iae.wrongType" +
                    listener.getClass().getName());
        }
    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        try {
            addListener(listenerClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("addListener listenerClass =" + listenerClass, e);
        }
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ServletException("addListener clazz =" + clazz, e);
        }
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        throw new UnsupportedOperationException("getJspConfigDescriptor");
    }

    @Override
    public ClassLoader getClassLoader() {
        return resourceManager.getClassLoader();
    }

    @Override
    public void declareRoles(String... roleNames) {
        throw new UnsupportedOperationException("declareRoles");
    }

    @Override
    public String getVirtualServerName() {
        return Version.getServerInfo()
                + (" (")
                + (serverAddress.getHostName())
                + (":")
                + (SystemPropertyUtil.get("user.name"))
                + (")");
    }

    @Override
    public String getRequestCharacterEncoding() {
        return requestCharacterEncoding;
    }

    @Override
    public void setRequestCharacterEncoding(String requestCharacterEncoding) {
        if (requestCharacterEncoding == null) {
            requestCharacterEncoding = HttpConstants.DEFAULT_CHARSET.name();
        }
        this.requestCharacterEncoding = requestCharacterEncoding;
        this.requestCharacterEncodingCharset = Charset.forName(requestCharacterEncoding);
    }

    @Override
    public String getResponseCharacterEncoding() {
        return responseCharacterEncoding;
    }

    @Override
    public void setResponseCharacterEncoding(String responseCharacterEncoding) {
        if (responseCharacterEncoding == null) {
            responseCharacterEncoding = HttpConstants.DEFAULT_CHARSET.name();
        }
        this.responseCharacterEncoding = responseCharacterEncoding;
        this.responseCharacterEncodingCharset = Charset.forName(responseCharacterEncoding);
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addJspFile(String jspName, String jspFile) {
        throw new UnsupportedOperationException("addJspFile");
    }
}
