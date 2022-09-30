package com.github.netty.protocol.servlet;

import com.github.netty.Version;
import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.FilterMapper;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.MimeMappingsX;
import com.github.netty.protocol.servlet.util.UrlMapper;
import com.github.netty.protocol.servlet.websocket.WebSocketServerContainer;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.util.concurrent.FastThreadLocal;

import javax.servlet.*;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;
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
    public static final String SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE = "javax.websocket.server.ServerContainer";
    //    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private final ServletErrorPageManager servletErrorPageManager = new ServletErrorPageManager();
    /**
     * Will not appear in the field in http body. multipart/form-data, application/x-www-form-urlencoded. （In order to avoid the client, you have been waiting for the client.）
     */
    private final Collection<String> notExistBodyParameters = new HashSet<>(Arrays.asList("_method", "JSESSIONID"));
    /**
     * Default: 20 minutes,
     */
    private int sessionTimeout = 1200;
    /**
     * Minimum upload file length, in bytes (becomes temporary file storage if larger than uploadMinSize)
     */
    private long uploadMinSize = 4096 * 16;
    /**
     * Upload file timeout millisecond , -1 is not control timeout.
     */
    private long uploadFileTimeoutMs = -1;
    private Map<String, Object> attributeMap = new LinkedHashMap<>(16);
    private Map<String, String> initParamMap = new LinkedHashMap<>(16);
    private Map<String, ServletRegistration> servletRegistrationMap = new LinkedHashMap<>(8);
    private Map<String, ServletFilterRegistration> filterRegistrationMap = new LinkedHashMap<>(8);
    private FastThreadLocal<Map<Charset, HttpDataFactory>> httpDataFactoryThreadLocal = new FastThreadLocal<Map<Charset, HttpDataFactory>>() {
        @Override
        protected Map<Charset, HttpDataFactory> initialValue() throws Exception {
            return new LinkedHashMap<>(5);
        }
    };
    private Set<SessionTrackingMode> defaultSessionTrackingModeSet = new HashSet<>(Arrays.asList(SessionTrackingMode.COOKIE, SessionTrackingMode.URL));
    private MimeMappingsX mimeMappings = new MimeMappingsX();
    private ServletEventListenerManager servletEventListenerManager = new ServletEventListenerManager();
    private ServletSessionCookieConfig sessionCookieConfig = new ServletSessionCookieConfig();
    private UrlMapper<ServletRegistration> servletUrlMapper = new UrlMapper<>(true);
    private FilterMapper<ServletFilterRegistration> filterUrlMapper = new FilterMapper<>();
    private ResourceManager resourceManager;
    private Supplier<Executor> asyncExecutorSupplier;
    private Supplier<Executor> defaultExecutorSupplier;
    private SessionService sessionService;
    private Set<SessionTrackingMode> sessionTrackingModeSet;
    private Servlet defaultServlet = new DefaultServlet();
    private boolean enableLookupFlag = false;
    private boolean autoFlush;
    private String serverHeader;
    private String contextPath = "";
    private String requestCharacterEncoding;
    private String responseCharacterEncoding;
    private String servletContextName;
    private InetSocketAddress serverAddress;
    private ClassLoader classLoader;
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

    public boolean isEnableLookupFlag() {
        return enableLookupFlag;
    }

    public void setEnableLookupFlag(boolean enableLookupFlag) {
        this.enableLookupFlag = enableLookupFlag;
    }

    public void setDocBase(String docBase) {
        String workspace = '/' + (serverAddress == null || HostUtil.isLocalhost(serverAddress.getHostName()) ? "localhost" : serverAddress.getHostName());
        setDocBase(docBase, workspace);
    }

    public void setDocBase(String docBase, String workspace) {
        ResourceManager old = this.resourceManager;
        this.resourceManager = new ResourceManager(docBase, workspace, classLoader);
        this.resourceManager.mkdirs("/");
        if (old != null) {
            getLog().warn("ServletContext docBase override. old = {}, new = {}", old, this.resourceManager);
        }
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = resourceManager.getRealPath("/");
        DiskAttribute.baseDirectory = resourceManager.getRealPath("/");
    }

    private LoggerX getLog() {
        return LoggerFactoryX.getLogger(contextPath);
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
        Map<Charset, HttpDataFactory> httpDataFactoryMap = httpDataFactoryThreadLocal.get();
        HttpDataFactory factory = httpDataFactoryMap.get(charset);
        if (factory == null) {
            factory = new DefaultHttpDataFactory(uploadMinSize, charset);
            httpDataFactoryMap.put(charset, factory);
        }
        return factory;
    }

    public String getServletPath(String absoluteUri) {
        return servletUrlMapper.getServletPath(absoluteUri);
    }

    public long getUploadMinSize() {
        return uploadMinSize;
    }

    public void setUploadMinSize(long uploadMinSize) {
        this.uploadMinSize = uploadMinSize;
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
        this.contextPath = normPath(contextPath);
        this.filterUrlMapper.setRootPath(contextPath);
        this.servletUrlMapper.setRootPath(contextPath);
    }

    @Override
    public ServletContext getContext(String uripath) {
        return this;
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
        if (extension.length() < 1) {
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
        return getRequestDispatcher(path, DispatcherType.REQUEST);
    }

    public ServletRequestDispatcher getRequestDispatcher(String path, DispatcherType dispatcherType) {
        UrlMapper.Element<ServletRegistration> element = servletUrlMapper.getMappingObjectByUri(path);
        if (element == null) {
            return null;
        }
        ServletRegistration servletRegistration = element.getObject();
        if (servletRegistration == null) {
            return null;
        }

        ServletFilterChain filterChain = ServletFilterChain.newInstance(this, servletRegistration);
        filterUrlMapper.addMappingObjectsByUri(path, dispatcherType, filterChain.getFilterRegistrationList());

        ServletRequestDispatcher dispatcher = ServletRequestDispatcher.newInstance(filterChain);
        dispatcher.setMapperElement(element);
        dispatcher.setPath(path);
        return dispatcher;
    }

    @Override
    public ServletRequestDispatcher getNamedDispatcher(String name) {
        ServletRegistration servletRegistration = null == name ? null : getServletRegistration(name);
        if (servletRegistration == null) {
            return null;
        }

        ServletFilterChain filterChain = ServletFilterChain.newInstance(this, servletRegistration);
        List<FilterMapper.Element<ServletFilterRegistration>> filterList = filterChain.getFilterRegistrationList();
        for (ServletFilterRegistration registration : filterRegistrationMap.values()) {
            for (String servletName : registration.getServletNameMappings()) {
                if (servletName.equals(name)) {
                    filterList.add(new FilterMapper.Element<>(name, registration));
                }
            }
        }

        ServletRequestDispatcher dispatcher = ServletRequestDispatcher.newInstance(filterChain);
        dispatcher.setName(name);
        return dispatcher;
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
        getLog().debug(msg);
    }

    @Override
    public void log(Exception exception, String msg) {
        getLog().debug(msg, exception);
    }

    @Override
    public void log(String message, Throwable throwable) {
        getLog().debug(message, throwable);
    }

    @Override
    public String getServerInfo() {
        return Version.getServerInfo()
                .concat("(JDK ")
                .concat(Version.getJvmVersion())
                .concat(";")
                .concat(Version.getOsName())
                .concat(" ")
                .concat(Version.getArch())
                .concat(")");
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
        ServletEventListenerManager listenerManager = getServletEventListenerManager();
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
        ServletEventListenerManager listenerManager = getServletEventListenerManager();
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

        ServletRegistration servletRegistration;
        if (newServlet == null) {
            servletRegistration = new ServletRegistration(servletName, servlet, this, servletUrlMapper);
        } else {
            servletRegistration = new ServletRegistration(servletName, newServlet, this, servletUrlMapper);
        }
        servletRegistrationMap.put(servletName, servletRegistration);
        return servletRegistration;
    }

    @Override
    public ServletRegistration addServlet(String servletName, Class<? extends Servlet> servletClass) {
        Servlet servlet = null;
        try {
            servlet = servletClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("createServlet error =" + e + ",servletName=" + servletName, e);
        }
        return addServlet(servletName, servlet);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        try {
            return clazz.getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
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
            return getDefaultSessionTrackingModes();
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
        ServletEventListenerManager listenerManager = getServletEventListenerManager();
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
                .concat(" (")
                .concat(serverAddress.getHostName())
                .concat(":")
                .concat(SystemPropertyUtil.get("user.name"))
                .concat(")");
    }

    @Override
    public String getRequestCharacterEncoding() {
        if (requestCharacterEncoding == null) {
            return HttpConstants.DEFAULT_CHARSET.name();
        }
        return requestCharacterEncoding;
    }

    @Override
    public void setRequestCharacterEncoding(String requestCharacterEncoding) {
        this.requestCharacterEncoding = requestCharacterEncoding;
    }

    @Override
    public String getResponseCharacterEncoding() {
        if (responseCharacterEncoding == null) {
            return HttpConstants.DEFAULT_CHARSET.name();
        }
        return responseCharacterEncoding;
    }

    @Override
    public void setResponseCharacterEncoding(String responseCharacterEncoding) {
        this.responseCharacterEncoding = responseCharacterEncoding;
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addJspFile(String jspName, String jspFile) {
        throw new UnsupportedOperationException("addJspFile");
    }
}
