package com.github.netty.protocol.servlet;

import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.MimeMappingsX;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.util.UrlMapper;
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
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Servlet context (lifetime same as server)
 * @author wangzihao
 *  2018/7/14/014
 */
public class ServletContext implements javax.servlet.ServletContext {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    /**
     * Default: 20 minutes,
     */
    private int sessionTimeout = 1200;
    /**
     * The maximum number of bytes written to the outputstream.writer () method of the servlet each time it is called is exceeded
     */
    private int responseWriterChunkMaxHeapByteLength = 4096;
    /**
     * Minimum upload file length, in bytes (becomes temporary file storage if larger than 16KB)
     */
    private long uploadMinSize = 4096 * 16;
    private Map<String,Object> attributeMap = new HashMap<>(16);
    private Map<String,String> initParamMap = new HashMap<>(16);
    private Map<String,ServletRegistration> servletRegistrationMap = new HashMap<>(8);
    private Map<String,ServletFilterRegistration> filterRegistrationMap = new HashMap<>(8);
    private FastThreadLocal<Map<Charset,HttpDataFactory>> httpDataFactoryThreadLocal = new FastThreadLocal<Map<Charset,HttpDataFactory>>(){
        @Override
        protected Map<Charset,HttpDataFactory> initialValue() throws Exception {
            return new HashMap<>(5);
        }
    };
    private Set<SessionTrackingMode> defaultSessionTrackingModeSet = new HashSet<>(Arrays.asList(SessionTrackingMode.COOKIE,SessionTrackingMode.URL));

//    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private ServletErrorPageManager servletErrorPageManager = new ServletErrorPageManager();
    private MimeMappingsX mimeMappings = new MimeMappingsX();
    private ServletEventListenerManager servletEventListenerManager = new ServletEventListenerManager();
    private ServletSessionCookieConfig sessionCookieConfig = new ServletSessionCookieConfig();
    private UrlMapper<ServletRegistration> servletUrlMapper = new UrlMapper<>(true);
    private UrlMapper<ServletFilterRegistration> filterUrlMapper = new UrlMapper<>(false);

    private ResourceManager resourceManager;
    private ExecutorService asyncExecutorService;
    private SessionService sessionService;
    private Set<SessionTrackingMode> sessionTrackingModeSet;

    private String serverHeader;
    private String contextPath;
    private String requestCharacterEncoding;
    private String responseCharacterEncoding;
    private String servletContextName;
    private InetSocketAddress serverAddress;
    private ClassLoader classLoader;

    public ServletContext(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? getClass().getClassLoader(): classLoader;
    }

    public void setServerAddress(InetSocketAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public void setDocBase(String docBase){
        String workspace = '/' + (serverAddress == null || HostUtil.isLocalhost(serverAddress.getHostName())? "localhost": serverAddress.getHostName());
        this.resourceManager = new ResourceManager(docBase,workspace,classLoader);
        this.resourceManager.mkdirs("/");

        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = resourceManager.getRealPath("/");
        DiskAttribute.baseDirectory = resourceManager.getRealPath("/");
    }

    public ExecutorService getAsyncExecutorService() {
        if(asyncExecutorService == null) {
            synchronized (this){
                if(asyncExecutorService == null) {
                    asyncExecutorService = new ThreadPoolX("Async",8);
//                            executorService = new DefaultEventExecutorGroup(15);
                }
            }
        }
        return asyncExecutorService;
    }

    public HttpDataFactory getHttpDataFactory(Charset charset){
        Map<Charset,HttpDataFactory> httpDataFactoryMap = httpDataFactoryThreadLocal.get();
        HttpDataFactory factory = httpDataFactoryMap.get(charset);
        if(factory == null){
            factory = new DefaultHttpDataFactory(uploadMinSize,charset);
            httpDataFactoryMap.put(charset, factory);
        }
        return factory;
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

    public void setServletContextName(String servletContextName) {
        this.servletContextName = servletContextName;
    }

    public void setServerHeader(String serverHeader) {
        this.serverHeader = serverHeader;
    }

    public String getServerHeader() {
        return serverHeader;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public ServletEventListenerManager getServletEventListenerManager() {
        return servletEventListenerManager;
    }

    public long getAsyncTimeout(){
        String value = getInitParameter("asyncTimeout");
        if(value == null){
            return 10000;
        }
        try {
            return Long.parseLong(value);
        }catch (NumberFormatException e){
            return 10000;
        }
    }

    public int getResponseWriterChunkMaxHeapByteLength() {
        return responseWriterChunkMaxHeapByteLength;
    }

    public void setResponseWriterChunkMaxHeapByteLength(int responseWriterChunkMaxHeapByteLength) {
        this.responseWriterChunkMaxHeapByteLength = responseWriterChunkMaxHeapByteLength;
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        if(sessionTimeout <= 0){
            return;
        }
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public ServletContext getContext(String uripath) {
        return this;
    }

    @Override
    public int getMajorVersion() {
        return 3;
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
        ServletRegistration servletRegistration = servletUrlMapper.getMappingObjectByUri(path);
        if(servletRegistration == null){
            return null;
        }

        ServletFilterChain filterChain = ServletFilterChain.newInstance(this,servletRegistration);
        filterUrlMapper.addMappingObjectsByUri(path,filterChain.getFilterRegistrationList());

        ServletRequestDispatcher dispatcher = ServletRequestDispatcher.newInstance(filterChain);
        dispatcher.setPath(path);
        return dispatcher;
    }

    @Override
    public ServletRequestDispatcher getNamedDispatcher(String name) {
        ServletRegistration servletRegistration = null == name ? null : getServletRegistration(name);
        if (servletRegistration == null) {
            return null;
        }

        ServletFilterChain filterChain = ServletFilterChain.newInstance(this,servletRegistration);
        List<ServletFilterRegistration> filterList = filterChain.getFilterRegistrationList();
        for (ServletFilterRegistration registration : filterRegistrationMap.values()) {
            for(String servletName : registration.getServletNameMappings()){
                if(servletName.equals(name)){
                    filterList.add(registration);
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
        if(registration == null){
            return null;
        }
        return registration.getServlet();
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        List<Servlet> list = new ArrayList<>();
        for(ServletRegistration registration : servletRegistrationMap.values()){
            list.add(registration.getServlet());
        }
        return Collections.enumeration(list);
    }

    @Override
    public Enumeration<String> getServletNames() {
        List<String> list = new ArrayList<>();
        for(ServletRegistration registration : servletRegistrationMap.values()){
            list.add(registration.getName());
        }
        return Collections.enumeration(list);
    }

    @Override
    public void log(String msg) {
        logger.debug(msg);
    }

    @Override
    public void log(Exception exception, String msg) {
        logger.debug(msg,exception);
    }

    @Override
    public void log(String message, Throwable throwable) {
        logger.debug(message,throwable);
    }

    @Override
    public String getServerInfo() {
        return ServletUtil.getServerInfo()
                .concat("(JDK ")
                .concat(ServletUtil.getJvmVersion())
                .concat(";")
                .concat(ServletUtil.getOsName())
                .concat(" ")
                .concat(ServletUtil.getArch())
                .concat(")");
    }

    @Override
    public String getInitParameter(String name) {
        return initParamMap.get(name);
    }

    public <T>T getInitParameter(String name,T def) {
        String value = getInitParameter(name);
        if(value == null){
            return def;
        }
        Class<?> clazz = def.getClass();
        Object valCast = TypeUtil.cast((Object) value,clazz);
        if(valCast != null && valCast.getClass().isAssignableFrom(clazz)){
            return (T) valCast;
        }
        return def;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParamMap.keySet());
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        return initParamMap.putIfAbsent(name,value) == null;
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
    public void setAttribute(String name, Object object) {
        Objects.requireNonNull(name);
        if(object == null){
            removeAttribute(name);
            return;
        }

        Object oldObject = attributeMap.put(name,object);
        ServletEventListenerManager listenerManager = getServletEventListenerManager();
        if(listenerManager.hasServletContextAttributeListener()){
            listenerManager.onServletContextAttributeAdded(new ServletContextAttributeEvent(this,name,object));
            if(oldObject != null){
                listenerManager.onServletContextAttributeReplaced(new ServletContextAttributeEvent(this,name,oldObject));
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object oldObject = attributeMap.remove(name);
        ServletEventListenerManager listenerManager = getServletEventListenerManager();
        if(listenerManager.hasServletContextAttributeListener()){
            listenerManager.onServletContextAttributeRemoved(new ServletContextAttributeEvent(this,name,oldObject));
        }
    }

    @Override
    public String getServletContextName() {
        return servletContextName;
    }

    @Override
    public ServletRegistration addServlet(String servletName, String className) {
        try {
            return addServlet(servletName, (Class<? extends Servlet>) Class.forName(className).newInstance());
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ServletRegistration addServlet(String servletName, Servlet servlet) {
        Servlet newServlet = servletEventListenerManager.onServletAdded(servlet);

        ServletRegistration servletRegistration;
        if(newServlet == null){
            servletRegistration = new ServletRegistration(servletName,servlet,this,servletUrlMapper);
        }else {
            servletRegistration = new ServletRegistration(servletName,newServlet,this,servletUrlMapper);
        }
        servletRegistrationMap.put(servletName,servletRegistration);
        return servletRegistration;
    }

    @Override
    public ServletRegistration addServlet(String servletName, Class<? extends Servlet> servletClass) {
        Servlet servlet = null;
        try {
            servlet = servletClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return addServlet(servletName,servlet);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
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
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ServletFilterRegistration addFilter(String filterName, Filter filter) {
        ServletFilterRegistration registration = new ServletFilterRegistration(filterName,filter,this,filterUrlMapper);
        filterRegistrationMap.put(filterName,registration);
        return registration;
    }

    @Override
    public ServletFilterRegistration addFilter(String filterName, Class<? extends Filter> filterClass) {
        try {
            return addFilter(filterName,filterClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
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
        if(sessionTrackingModeSet == null){
            return getDefaultSessionTrackingModes();
        }
        return sessionTrackingModeSet;
    }

    @Override
    public void addListener(String className) {
        try {
            addListener((Class<? extends EventListener>) Class.forName(className));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public <T extends EventListener> void addListener(T listener) {
        Objects.requireNonNull(listener);

        ServletEventListenerManager listenerManager = getServletEventListenerManager();
        if(listener instanceof ServletContextAttributeListener){
            listenerManager.addServletContextAttributeListener((ServletContextAttributeListener) listener);

        }else if(listener instanceof ServletRequestListener){
            listenerManager.addServletRequestListener((ServletRequestListener) listener);

        }else if(listener instanceof ServletRequestAttributeListener){
            listenerManager.addServletRequestAttributeListener((ServletRequestAttributeListener) listener);

        }else if(listener instanceof HttpSessionIdListener){
            listenerManager.addHttpSessionIdListenerListener((HttpSessionIdListener) listener);

        }else if(listener instanceof HttpSessionAttributeListener){
            listenerManager.addHttpSessionAttributeListener((HttpSessionAttributeListener) listener);

        }else if(listener instanceof HttpSessionListener){
            listenerManager.addHttpSessionListener((HttpSessionListener) listener);

        }else if(listener instanceof ServletContextListener){
            listenerManager.addServletContextListener((ServletContextListener) listener);

        }else {
            throw new IllegalArgumentException("applicationContext.addListener.iae.wrongType"+
                    listener.getClass().getName());
        }
    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        try {
            addListener(listenerClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return resourceManager.getClassLoader();
    }

    @Override
    public void declareRoles(String... roleNames) {

    }

    @Override
    public String getVirtualServerName() {
        return ServletUtil.getServerInfo()
        .concat(" (")
        .concat(serverAddress.getHostName())
        .concat(":")
        .concat(SystemPropertyUtil.get("user.name"))
        .concat(")");
    }

    @Override
    public String getRequestCharacterEncoding() {
        if(requestCharacterEncoding == null){
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
        if(responseCharacterEncoding == null){
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
        // TODO: 2018/11/11/011  addJspFile
        return null;
    }

}
