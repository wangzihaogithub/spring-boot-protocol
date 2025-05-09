package com.github.netty.protocol.servlet;

import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.util.UrlMapper;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Servlet request asynchronous
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletHttpAsyncRequest extends HttpServletRequestWrapper {

    /**
     * The parameter key required by the servlet specification
     * The set of attribute names that are special for request dispatchers.
     */
    private static final String[] specials = {
            AsyncContext.ASYNC_REQUEST_URI,
            AsyncContext.ASYNC_CONTEXT_PATH,
            AsyncContext.ASYNC_SERVLET_PATH,
            AsyncContext.ASYNC_PATH_INFO,
            AsyncContext.ASYNC_QUERY_STRING
    };
    /**
     * The parameter values required by the servlet specification
     * Special attributes.
     */
    private final Object[] specialAttributes = new Object[specials.length];
    private final ServletAsyncContext servletAsyncContext;
    private String pathInfo = null;
    private String queryString = null;
    private String requestURI = null;
    private String relativePathNoQueryString = null;
    private String servletPath = null;
    private UrlMapper.Element<ServletRegistration> mapperElement;
    private Map<String, String[]> parameterMap = null;
    private boolean decodeParameterFlag = false;
    private int decodePathsQueryIndex = -1;
    private String dispatchPath;
    private String contextPath;

    private ServletHttpAsyncRequest(HttpServletRequest source, ServletAsyncContext servletAsyncContext) {
        super(source);
        this.servletAsyncContext = servletAsyncContext;
    }

    public static ServletHttpAsyncRequest newInstanceAsyncPath(HttpServletRequest source, ServletAsyncContext servletAsyncContext,
                                                               String dispatchPath, String relativePathNoQueryString, String contextPath,
                                                               int queryIndex, UrlMapper.Element<ServletRegistration> mapperElement) {
        ServletHttpAsyncRequest request = new ServletHttpAsyncRequest(source, servletAsyncContext);
        request.dispatchPath = dispatchPath;
        request.relativePathNoQueryString = relativePathNoQueryString;
        request.contextPath = contextPath;
        request.servletPath = mapperElement.getServletPath(relativePathNoQueryString);
        request.decodePathsQueryIndex = queryIndex;
        request.mapperElement = mapperElement;
        request.requestURI = contextPath + relativePathNoQueryString;
        if (source.getAttribute(AsyncContext.ASYNC_REQUEST_URI) == null) {
            request.specialAttributes[0] = request.getRequestURI();
            request.specialAttributes[1] = request.getContextPath();
            request.specialAttributes[2] = request.getServletPath();
            request.specialAttributes[3] = request.getPathInfo();
            request.specialAttributes[4] = request.getQueryString();
        }
        return request;
    }

    @Override
    public ServletAsyncContext getAsyncContext() {
        return servletAsyncContext;
    }

    @Override
    public com.github.netty.protocol.servlet.ServletContext getServletContext() {
        return servletAsyncContext.servletContext;
    }

    @Override
    public void setRequest(ServletRequest servletRequest) {
        throw new UnsupportedOperationException("Unsupported Method On Async setRequest ");
    }

    @Override
    public ServletRequestDispatcher getRequestDispatcher(String path) {
        return servletAsyncContext.servletContext.getRequestDispatcher(path, getDispatcherType(), true);
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.ASYNC;
    }

    @Override
    public String getContextPath() {
        return contextPath;
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

    @Override
    public String getPathInfo() {
        if (this.pathInfo == null && dispatchPath != null && mapperElement != null) {
            this.pathInfo = mapperElement.getPathInfo(dispatchPath, decodePathsQueryIndex);
        }
        return pathInfo;
    }

    @Override
    public String getQueryString() {
        if (decodePathsQueryIndex != -1) {
            this.queryString = dispatchPath.substring(decodePathsQueryIndex + 1);
        }
        return queryString;
    }

    @Override
    public String getRequestURI() {
        return this.requestURI;
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    @Override
    public String[] getParameterValues(String name) {
        return getParameterMap().get(name);
    }

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

    @Override
    public Map<String, String[]> getParameterMap() {
        if (!decodeParameterFlag) {
            decodeParameter();
        }
        return parameterMap;
    }

    @Override
    public String getParameter(String name) {
        String[] values = getParameterMap().get(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    /**
     * Parse forward parameter
     */
    private void decodeParameter() {
        Map<String, String[]> sourceParameterMap = super.getParameterMap();
        Map<String, String[]> parameterMap = new LinkedHashMap<>(sourceParameterMap);
        if (decodePathsQueryIndex != -1) {
            Charset charset = Charset.forName(getCharacterEncoding());
            ServletUtil.decodeByUrl(parameterMap, dispatchPath, charset);
        }
        this.parameterMap = Collections.unmodifiableMap(parameterMap);
        this.decodeParameterFlag = true;
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * Override the <code>getAttribute()</code> method of the wrapped request.
     *
     * @param name Name of the attribute to retrieve
     * @return attribute
     */
    @Override
    public Object getAttribute(String name) {
        int pos = getSpecial(name);
        if (pos == -1) {
            return getRequest().getAttribute(name);
        } else {
            Object value = specialAttributes[pos];
            if (value != null) {
                return value;
            } else {
                return getRequest().getAttribute(name);
            }
        }
    }

    /**
     * Override the <code>getAttributeNames()</code> method of the wrapped
     * request.
     *
     * @return attributeNames
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return new AttributeNamesEnumerator();
    }

    /**
     * Override the <code>removeAttribute()</code> method of the
     * wrapped request.
     *
     * @param name Name of the attribute to remove
     */
    @Override
    public void removeAttribute(String name) {
        if (!removeSpecial(name)) {
            getRequest().removeAttribute(name);
        }
    }

    /**
     * Override the <code>setAttribute()</code> method of the
     * wrapped request.
     *
     * @param name  Name of the attribute to set
     * @param value Value of the attribute to set
     */
    @Override
    public void setAttribute(String name, Object value) {
        if (!setSpecial(name, value)) {
            getRequest().setAttribute(name, value);
        }
    }

    /**
     * Is this attribute name one of the special ones that is added only for
     * included servlets?
     *
     * @param name Attribute name to be tested
     */
    protected boolean isSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a special attribute.
     *
     * @return the special attribute pos, or -1 if it is not a special
     * attribute
     */
    protected int getSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Set a special attribute.
     *
     * @return true if the attribute was a special attribute, false otherwise
     */
    protected boolean setSpecial(String name, Object value) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                specialAttributes[i] = value;
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a special attribute.
     *
     * @return true if the attribute was a special attribute, false otherwise
     */
    protected boolean removeSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                specialAttributes[i] = null;
                return true;
            }
        }
        return false;
    }

    // ----------------------------------- AttributeNamesEnumerator Inner Class

    /**
     * Utility class used to expose the special attributes as being available
     * as request attributes.
     */
    protected class AttributeNamesEnumerator implements Enumeration<String> {
        protected final int last;
        protected final Enumeration<String> parentEnumeration;
        protected int pos = -1;
        protected String next = null;

        public AttributeNamesEnumerator() {
            int last = -1;
            parentEnumeration = getRequest().getAttributeNames();
            for (int i = specialAttributes.length - 1; i >= 0; i--) {
                if (getAttribute(specials[i]) != null) {
                    last = i;
                    break;
                }
            }
            this.last = last;
        }

        @Override
        public boolean hasMoreElements() {
            return ((pos != last) || (next != null)
                    || ((next = findNext()) != null));
        }

        @Override
        public String nextElement() {
            if (pos != last) {
                for (int i = pos + 1; i <= last; i++) {
                    if (getAttribute(specials[i]) != null) {
                        pos = i;
                        return (specials[i]);
                    }
                }
            }
            String result = next;
            if (next != null) {
                next = findNext();
            } else {
                throw new NoSuchElementException();
            }
            return result;
        }

        protected String findNext() {
            String result = null;
            while ((result == null) && (parentEnumeration.hasMoreElements())) {
                String current = parentEnumeration.nextElement();
                if (!isSpecial(current)) {
                    result = current;
                }
            }
            return result;
        }
    }

}
