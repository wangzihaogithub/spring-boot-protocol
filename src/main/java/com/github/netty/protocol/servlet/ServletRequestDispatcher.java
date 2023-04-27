package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.util.UrlMapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet request scheduling
 *
 * @author wangzihao
 * 2018/7/14/014
 */
public class ServletRequestDispatcher implements RequestDispatcher, Recyclable {
    private static final Recycler<ServletRequestDispatcher> RECYCLER = new Recycler<>(ServletRequestDispatcher::new);
    /**
     * Scheduling path (mutually exclusive with name field)
     */
    private String path;
    /**
     * Scheduling servlet name (mutually exclusive with path field)
     */
    private String name;
    /**
     * Match mapping
     */
    private UrlMapper.Element<ServletRegistration> mapperElement;
    /**
     * The filter chain
     */
    private ServletFilterChain filterChain;

    private ServletRequestDispatcher() {
    }

    public static ServletRequestDispatcher newInstance(ServletFilterChain filterChain) {
        ServletRequestDispatcher instance = RECYCLER.getInstance();
        instance.filterChain = filterChain;
        return instance;
    }

    public static String getPathInfo(String path, UrlMapper.Element<ServletRegistration> mapper) {
        if (path == null) {
            return null;
        }
        if (mapper.isAllPatternFlag() || !mapper.getPattern().endsWith("*")) {
            return null;
        }
        int firstWildcardIndex = mapper.getFirstWildcardIndex();
        if (firstWildcardIndex != -1) {
            int begin = -1;
            for (int i = 0; i < path.length(); i++) {
                switch (path.charAt(i)) {
                    case '/': {
                        break;
                    }
                    case '?': {
                        if (begin == -1) {
                            return null;
                        } else if (begin > i) {
                            return null;
                        } else {
                            return ServletContext.normPath(path.substring(begin, i));
                        }
                    }
                    default: {
                        if (begin == -1) {
                            begin = i + firstWildcardIndex;
                        }
                        break;
                    }
                }
            }
            if (begin != -1) {
                if (begin == path.length()) {
                    return "";
                } else if (begin < path.length()) {
                    return ServletContext.normPath(path.substring(begin));
                }
            }
        }
        return null;
    }

    /**
     * Forward to other servlets for processing (note: transfer control of the response to other servlets)
     *
     * @param request  request
     * @param response response
     * @throws ServletException ServletException
     * @throws IOException      IOException
     */
    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        forward(request, response, DispatcherType.FORWARD);
    }

    void forward(ServletRequest request, ServletResponse response, DispatcherType dispatcherType) throws ServletException, IOException {
        ServletHttpServletResponse httpResponse = ServletUtil.unWrapper(response);
        if (httpResponse == null) {
            throw new UnsupportedOperationException("Not found Original Response");
        }
        HttpServletRequest httpRequest = ServletUtil.unWrapper(request);
        if (httpRequest == null) {
            throw new UnsupportedOperationException("Not found Original Request");
        }
        if (response.isCommitted()) {
            throw new IOException("Cannot perform this operation after response has been committed");
        }

        //Hand over control of the output stream
        ServletOutputStreamWrapper outWrapper = httpResponse.getOutputStream();
        //Pause the current response
        outWrapper.setSuspendFlag(true);
        //To the next servlet
        ServletHttpForwardResponse forwardResponse = new ServletHttpForwardResponse(httpResponse, outWrapper.unwrap());
        // ServletHttpForwardRequest. The class will be passed on new data
        ServletHttpForwardRequest forwardRequest = new ServletHttpForwardRequest(httpRequest);

        //According to the name
        if (path == null) {
            forwardRequest.setForwardName(name);
            forwardRequest.setPaths(httpRequest.getPathInfo(), httpRequest.getQueryString(), httpRequest.getRequestURI(), httpRequest.getServletPath());
            forwardRequest.setParameterMap(httpRequest.getParameterMap());
        } else {
            forwardRequest.setForwardPath(path);
            //According to the path
            if (forwardRequest.getAttribute(FORWARD_REQUEST_URI) == null) {
                forwardRequest.setAttribute(FORWARD_REQUEST_URI, httpRequest.getRequestURI());
                forwardRequest.setAttribute(FORWARD_CONTEXT_PATH, httpRequest.getContextPath());
                forwardRequest.setAttribute(FORWARD_PATH_INFO, httpRequest.getPathInfo());
                forwardRequest.setAttribute(FORWARD_QUERY_STRING, httpRequest.getQueryString());
                forwardRequest.setAttribute(FORWARD_SERVLET_PATH, httpRequest.getServletPath());
            }
        }
        forwardRequest.setDispatcherType(dispatcherType);
        forwardRequest.setDispatcher(this);
        try {
            dispatch(forwardRequest, forwardResponse);
        } finally {
            recycle();
        }
    }

    /**
     * Introduction of response content from other servlets (note: other servlets can write data, but cannot submit data)
     * Premise: transfer-encoding is required
     *
     * @param request  request
     * @param response response
     * @throws ServletException ServletException
     * @throws IOException      IOException
     */
    @Override
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        include(request, response, DispatcherType.INCLUDE);
    }

    void include(ServletRequest request, ServletResponse response, DispatcherType dispatcherType) throws ServletException, IOException {
        ServletHttpServletResponse httpResponse = ServletUtil.unWrapper(response);
        if (httpResponse == null) {
            throw new UnsupportedOperationException("Not found Original Response");
        }
        HttpServletRequest httpRequest = ServletUtil.unWrapper(request);
        if (httpRequest == null) {
            throw new UnsupportedOperationException("Not found Original Request");
        }

        // ServletHttpIncludeResponse. The class will prohibit operation data
        ServletHttpIncludeResponse includeResponse = new ServletHttpIncludeResponse(httpResponse);
        // ServletHttpIncludeRequest. The class will be passed on new data
        ServletHttpIncludeRequest includeRequest = new ServletHttpIncludeRequest(httpRequest);

        //According to the name
        if (path == null) {
            includeRequest.setIncludeName(name);
            includeRequest.setPaths(httpRequest.getPathInfo(), httpRequest.getQueryString(), httpRequest.getRequestURI(), httpRequest.getServletPath());
            includeRequest.setParameterMap(httpRequest.getParameterMap());
        } else {
            includeRequest.setIncludePath(path);
            //According to the path
            if (includeRequest.getAttribute(INCLUDE_REQUEST_URI) == null) {
                includeRequest.setAttribute(INCLUDE_REQUEST_URI, includeRequest.getRequestURI());
                includeRequest.setAttribute(INCLUDE_CONTEXT_PATH, includeRequest.getContextPath());
                includeRequest.setAttribute(INCLUDE_PATH_INFO, includeRequest.getPathInfo());
                includeRequest.setAttribute(INCLUDE_QUERY_STRING, includeRequest.getQueryString());
                includeRequest.setAttribute(INCLUDE_SERVLET_PATH, includeRequest.getServletPath());
            }
        }
        includeRequest.setDispatcherType(dispatcherType);
        includeRequest.setDispatcher(this);
        try {
            dispatch(includeRequest, includeResponse);
        } finally {
            recycle();
        }
    }

    /**
     * dispatch
     *
     * @param request  request
     * @param response response
     * @throws ServletException ServletException
     * @throws IOException      IOException
     */
    public void dispatch(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }

    /**
     * dispatch (asynchronous)
     *
     * @param request      request
     * @param response     response
     * @param asyncContext asyncContext
     * @throws ServletException ServletException
     * @throws IOException      IOException
     */
    public void dispatchAsync(HttpServletRequest request, HttpServletResponse response, ServletAsyncContext asyncContext) throws ServletException, IOException {
        if (path == null) {
            return;
        }
        if (request instanceof ServletHttpAsyncRequest
                && path.equals(request.getAttribute(AsyncContext.ASYNC_REQUEST_URI))) {
            throw new IllegalStateException("Asynchronous dispatch operation has already been called. Additional asynchronous dispatch operation within the same asynchronous cycle is not allowed.");
        }

        ServletHttpServletResponse httpResponse = ServletUtil.unWrapper(response);
        if (httpResponse == null) {
            throw new UnsupportedOperationException("Not found Original Response");
        }
        HttpServletRequest httpRequest = ServletUtil.unWrapper(request);
        if (httpRequest == null) {
            throw new UnsupportedOperationException("Not found Original Request");
        }
        if (response.isCommitted()) {
            throw new IllegalStateException("Cannot perform this operation after response has been committed");
        }

        //Hand over control of the output stream
        ServletOutputStreamWrapper outWrapper = httpResponse.getOutputStream();

        //Pause the current response
        outWrapper.setSuspendFlag(true);
        //To the next servlet
        ServletHttpAsyncResponse asyncResponse = new ServletHttpAsyncResponse(httpResponse, outWrapper.unwrap());
        ServletHttpAsyncRequest asyncRequest = new ServletHttpAsyncRequest(request, asyncContext);
        asyncRequest.setDispatchPath(path);
        if (request.getAttribute(AsyncContext.ASYNC_REQUEST_URI) == null) {
            asyncRequest.setAttribute(AsyncContext.ASYNC_CONTEXT_PATH, asyncRequest.getContextPath());
            asyncRequest.setAttribute(AsyncContext.ASYNC_PATH_INFO, asyncRequest.getPathInfo());
            asyncRequest.setAttribute(AsyncContext.ASYNC_QUERY_STRING, asyncRequest.getQueryString());
            asyncRequest.setAttribute(AsyncContext.ASYNC_REQUEST_URI, asyncRequest.getRequestURI());
            asyncRequest.setAttribute(AsyncContext.ASYNC_SERVLET_PATH, asyncRequest.getServletPath());
        }
        asyncRequest.setDispatcher(this);
        //Return to the task
        try {
            dispatch(asyncRequest, asyncResponse);
        } finally {
            recycle();
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        if (filterChain == null) {
            return name;
        }
        return filterChain.getServletRegistration().getName();
    }

    public void setName(String name) {
        this.name = name;
    }

    public ServletFilterChain getFilterChain() {
        return filterChain;
    }

    public UrlMapper.Element<ServletRegistration> getMapperElement() {
        return mapperElement;
    }

    void setMapperElement(UrlMapper.Element<ServletRegistration> mapperElement) {
        this.mapperElement = mapperElement;
    }

    @Override
    public void recycle() {
        if (filterChain == null) {
            return;
        }
        filterChain.recycle();
        path = null;
        name = null;
        mapperElement = null;
        filterChain = null;
        RECYCLER.recycleInstance(this);
    }

}
