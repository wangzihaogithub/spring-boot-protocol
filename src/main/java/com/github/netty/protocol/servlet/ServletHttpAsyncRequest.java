package com.github.netty.protocol.servlet;

import com.github.netty.protocol.servlet.util.HttpConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;

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
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletHttpAsyncRequest extends HttpServletRequestWrapper{

    private String pathInfo = null;
    private String queryString = null;
    private String requestURI = null;
    private String servletPath = null;

    private Map<String, String[]> parameterMap = null;

    private boolean decodePathsFlag = false;
    private boolean decodeParameterFlag = false;

    private String dispatchPath;

    /**
     * The parameter key required by the servlet specification
     * The set of attribute names that are special for request dispatchers.
     */
    private static final String specials[] = {
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

    private ServletAsyncContext servletAsyncContext;

    public ServletHttpAsyncRequest(HttpServletRequest source,ServletAsyncContext servletAsyncContext) {
        super(source);
        this.servletAsyncContext = servletAsyncContext;
    }

    public void setDispatchPath(String dispatchPath) {
        this.dispatchPath = dispatchPath;
    }

    @Override
    public ServletAsyncContext getAsyncContext() {
        return servletAsyncContext;
    }

    @Override
    public ServletContext getServletContext() {
        return servletAsyncContext.getServletContext();
    }

    @Override
    public void setRequest(ServletRequest servletRequest) {
        throw new UnsupportedOperationException("Unsupported Method On Forward setRequest ");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.FORWARD;
    }

    @Override
    public String getContextPath() {
        return super.getContextPath();
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
        if(!decodePathsFlag){
            decodePaths();
        }
        return pathInfo;
    }

    @Override
    public String getQueryString() {
        if(!decodePathsFlag){
            decodePaths();
        }
        return queryString;
    }

    @Override
    public String getRequestURI() {
        if(!decodePathsFlag){
            decodePaths();
        }
        return requestURI;
    }

    @Override
    public String getServletPath() {
        if(!decodePathsFlag){
            decodePaths();
        }
        return servletPath;
    }

    public void setPaths(String pathInfo,String queryString,String requestURI,String servletPath) {
        this.pathInfo = pathInfo;
        this.queryString = queryString;
        this.requestURI = requestURI;
        this.servletPath = servletPath;
        this.decodePathsFlag = true;
    }

    public void setParameterMap(Map<String, String[]> parameterMap) {
        this.parameterMap = parameterMap;
        this.decodeParameterFlag = true;
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

    @Override
    public Map<String, String[]> getParameterMap() {
        if(!decodeParameterFlag){
            decodeParameter();
        }
        return parameterMap;
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

    /**
     * Parsing path
     */
    private void decodePaths(){
        ServletContext servletContext = getServletContext();
        String contextPath = servletContext.getContextPath();
        boolean existContextPath = contextPath != null && contextPath.length() > 0;

        String sourceURI = dispatchPath;
        if (sourceURI.indexOf('\\') > -1) {
            sourceURI = sourceURI.replace('\\', '/');
        }
        String servletPath = existContextPath? sourceURI.replace(contextPath, "") : sourceURI;
        if (servletPath.isEmpty() || servletPath.charAt(0)!= '/') {
            servletPath = '/' + servletPath;
        }

        //Parsing the queryString
        int queryInx = servletPath.indexOf('?');
        if (queryInx > -1) {
            this.queryString = servletPath.substring(queryInx + 1, servletPath.length());
            servletPath = servletPath.substring(0, queryInx);
        }

        //Parse the requestURI and ensure that the requestURI prefix is + /
        String requestURI;
        if(existContextPath){
            requestURI = '/' + contextPath + servletPath;
        }else {
            requestURI = servletPath;
        }

        this.servletPath = servletPath;
        this.requestURI = requestURI;
        // 1.Plus the pathInfo
        this.pathInfo = null;
        this.decodePathsFlag = true;
    }

    /**
     * Parse forward parameter
     */
    private void decodeParameter(){
        Map<String,String[]> sourceParameterMap = super.getParameterMap();
        Map<String,String[]> parameterMap = new HashMap<>(sourceParameterMap);
        Charset charset = Charset.forName(getCharacterEncoding());
        ServletUtil.decodeByUrl(parameterMap, dispatchPath,charset);

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
     * @param name Name of the attribute to set
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
     *         attribute
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
        protected int pos = -1;
        protected final int last;
        protected final Enumeration<String> parentEnumeration;
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
