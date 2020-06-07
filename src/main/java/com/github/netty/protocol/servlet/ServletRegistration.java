package com.github.netty.protocol.servlet;

import com.github.netty.protocol.servlet.util.UrlMapper;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletSecurityElement;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The servlet supportPipeline
 * @author wangzihao
 *  2018/7/14/014
 */
public class ServletRegistration implements javax.servlet.ServletRegistration, javax.servlet.ServletRegistration.Dynamic {
    private String servletName;
    private Servlet servlet;
    private ServletConfig servletConfig;
    private ServletContext servletContext;
    private UrlMapper<ServletRegistration> urlMapper;
    private MultipartConfigElement multipartConfigElement;
    private ServletSecurityElement servletSecurityElement;
    private String roleName;
    private boolean asyncSupported = true;
    private int loadOnStartup = -1;
    private Map<String,String> initParameterMap = new HashMap<>();
    private Set<String> mappingSet = new HashSet<String>(){
        @Override
        public boolean add(String pattern) {
            urlMapper.addMapping(pattern, ServletRegistration.this, servletName);
            return super.add(pattern);
        }

        @Override
        public boolean addAll(Collection c) {
            for (Object o : c) {
                add(o.toString());
            }
            return c.size() > 0;
        }
    };
    private AtomicBoolean initServlet = new AtomicBoolean();
    private Set<String> servletSecuritys = new LinkedHashSet<>();

    public ServletRegistration(String servletName, Servlet servlet, ServletContext servletContext, UrlMapper<ServletRegistration> urlMapper) {
        this.servletName = servletName;
        this.servlet = servlet;
        this.servletContext = servletContext;
        this.urlMapper = urlMapper;
        this.servletConfig = new ServletConfig() {
            @Override
            public String getServletName() {
                return ServletRegistration.this.servletName;
            }

            @Override
            public javax.servlet.ServletContext getServletContext() {
                return ServletRegistration.this.servletContext;
            }

            @Override
            public String getInitParameter(String name) {
                return ServletRegistration.this.getInitParameter(name);
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(ServletRegistration.this.getInitParameters().keySet());
            }
        };
    }

    public ServletSecurityElement getServletSecurityElement() {
        return servletSecurityElement;
    }

    public MultipartConfigElement getMultipartConfigElement() {
        return multipartConfigElement;
    }

    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    public Servlet getServlet() {
        return servlet;
    }

    public Boolean isAsyncSupported() {
        return asyncSupported;
    }

    public int getLoadOnStartup() {
        return loadOnStartup;
    }

    public boolean isInitServletCas(boolean expect, boolean update) {
        return initServlet.compareAndSet(expect,update);
    }

    public boolean isInitServlet() {
        return initServlet.get();
    }

    @Override
    public Set<String> addMapping(String... urlPatterns) {
        mappingSet.addAll(Arrays.asList(urlPatterns));
        return mappingSet;
    }

    @Override
    public Collection<String> getMappings() {
        return mappingSet;
    }

    @Override
    public String getRunAsRole() {
        return roleName;
    }

    @Override
    public String getName() {
        return servletName;
    }

    @Override
    public String getClassName() {
        return servlet.getClass().getName();
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        return initParameterMap.put(name,value) != null;
    }

    @Override
    public String getInitParameter(String name) {
        return initParameterMap.get(name);
    }

    @Override
    public Set<String> setInitParameters(Map<String, String> initParameters) {
        this.initParameterMap = initParameters;
        return initParameterMap.keySet();
    }

    @Override
    public Map<String, String> getInitParameters() {
        return initParameterMap;
    }

    @Override
    public void setLoadOnStartup(int loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
    }

    @Override
    public Set<String> setServletSecurity(ServletSecurityElement constraint) {
        this.servletSecurityElement = constraint;
        servletSecuritys.addAll(servletSecurityElement.getMethodNames());
        return servletSecuritys;
    }

    @Override
    public void setMultipartConfig(MultipartConfigElement multipartConfig) {
        this.multipartConfigElement = multipartConfig;
    }

    @Override
    public void setRunAsRole(String roleName) {
        this.roleName = roleName;
    }

    @Override
    public void setAsyncSupported(boolean isAsyncSupported) {
        this.asyncSupported = isAsyncSupported;
    }

    @Override
    public String toString() {
        return getName();
    }
}
