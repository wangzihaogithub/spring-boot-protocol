package com.github.netty.protocol.servlet;

import com.github.netty.protocol.servlet.util.FilterMapper;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * servlet Filter registration
 * @author wangzihao
 *  2018/7/14/014
 */
public class ServletFilterRegistration implements FilterRegistration,FilterRegistration.Dynamic {

    private String filterName;
    private Filter filter;
    private FilterConfig filterConfig;
    private ServletContext servletContext;
    private FilterMapper<ServletFilterRegistration> urlMapper;
    private boolean asyncSupported = true;
    private Map<String,String> initParameterMap = new LinkedHashMap<>();
    private MappingSet mappingSet = new MappingSet();
    class MappingSet extends LinkedHashSet<String>{
        @Override
        public boolean add(String pattern) {
            return add(pattern,false,null);
        }

        @Override
        public boolean addAll(Collection c) {
            for (Object o : c) {
                add(o.toString());
            }
            return c.size() > 0;
        }

        public boolean add(String pattern,boolean isMatchAfter,EnumSet<DispatcherType> dispatcherTypes) {
            urlMapper.addMapping(pattern, ServletFilterRegistration.this, filterName,isMatchAfter,dispatcherTypes);
            return super.add(pattern);
        }

        @Override
        public void clear() {
            urlMapper.clear();
            super.clear();
        }
    }
    private Set<String> servletNameMappingSet = new HashSet<>();
    private AtomicBoolean initFilter = new AtomicBoolean();

    public ServletFilterRegistration(String filterName, Filter servlet, ServletContext servletContext, FilterMapper<ServletFilterRegistration> urlMapper) {
        this.filterName = filterName;
        this.filter = servlet;
        this.servletContext = servletContext;
        this.urlMapper = urlMapper;
        this.filterConfig = new FilterConfig(){
            @Override
            public String getFilterName() {
                return ServletFilterRegistration.this.filterName;
            }

            @Override
            public ServletContext getServletContext() {
                return ServletFilterRegistration.this.servletContext;
            }

            @Override
            public String getInitParameter(String name) {
                return ServletFilterRegistration.this.getInitParameter(name);
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(ServletFilterRegistration.this.getInitParameters().keySet());
            }
        };
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public Filter getFilter() {
        return filter;
    }

    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    public boolean isInitFilterCas(boolean expect, boolean update) {
        return initFilter.compareAndSet(expect,update);
    }

    public boolean isInitFilter() {
        return initFilter.get();
    }

    @Override
    public String getName() {
        return filterName;
    }

    @Override
    public String getClassName() {
        return filter.getClass().getName();
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
    public void setAsyncSupported(boolean isAsyncSupported) {
        this.asyncSupported = isAsyncSupported;
    }

    @Override
    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames) {
        if(servletNames != null) {
            servletNameMappingSet.addAll(Arrays.asList(servletNames));
        }
    }

    @Override
    public Collection<String> getServletNameMappings() {
        return servletNameMappingSet;
    }

    @Override
    public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns) {
        if(urlPatterns != null) {
            for (String urlPattern : urlPatterns) {
                mappingSet.add(urlPattern, isMatchAfter,dispatcherTypes);
            }
        }
    }

    @Override
    public Collection<String> getUrlPatternMappings() {
        return mappingSet;
    }

    @Override
    public String toString() {
        return getName();
    }
}
