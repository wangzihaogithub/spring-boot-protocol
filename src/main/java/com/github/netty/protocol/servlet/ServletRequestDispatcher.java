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
    int queryIndex;
    /**
     * Match mapping
     */
    UrlMapper.Element<ServletRegistration> mapperElement;
    /**
     * The filter chain
     */
    ServletFilterChain filterChain;
    /**
     * url
     * 1. no contextPath
     * 2. exist querystring
     */
    String path;
    /**
     * url
     * 1. no contextPath
     * 2. no exist querystring
     */
    String relativePathNoQueryString;
    String contextPath;
    /**
     * Scheduling servlet name (mutually exclusive with path field)
     */
    String name;

    private ServletRequestDispatcher() {
    }

    public static ServletRequestDispatcher newInstancePath(ServletFilterChain filterChain,
                                                           String path,
                                                           String contextPath,
                                                           String relativePathNoQueryString,
                                                           UrlMapper.Element<ServletRegistration> element,
                                                           int queryIndex) {
        ServletRequestDispatcher instance = RECYCLER.getInstance();
        instance.filterChain = filterChain;
        instance.mapperElement = element;
        instance.path = path;
        instance.relativePathNoQueryString = relativePathNoQueryString;
        instance.contextPath = contextPath;
        instance.queryIndex = queryIndex;
        return instance;
    }

    public static ServletRequestDispatcher newInstanceName(ServletFilterChain filterChain,
                                                           String name,
                                                           String contextPath) {
        ServletRequestDispatcher instance = RECYCLER.getInstance();
        instance.filterChain = filterChain;
        instance.name = name;
        instance.contextPath = contextPath;
        return instance;
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
        // ServletHttpForwardRequest. The class will be passed on new data
        ServletHttpForwardRequest forwardRequest;

        //According to the name
        if (path != null) {
            forwardRequest = ServletHttpForwardRequest.newInstanceForwardPath(httpRequest, path, relativePathNoQueryString, contextPath, queryIndex, mapperElement, dispatcherType);
        } else {
            forwardRequest = ServletHttpForwardRequest.newInstanceForwardName(httpRequest, name, contextPath, dispatcherType);
        }
        ServletHttpForwardResponse forwardResponse = new ServletHttpForwardResponse(httpResponse, outWrapper.unwrap());
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

        // ServletHttpIncludeRequest. The class will be passed on new data
        ServletHttpIncludeRequest includeRequest;
        if (path != null) {
            includeRequest = ServletHttpIncludeRequest.newInstanceIncludePath(httpRequest, path, relativePathNoQueryString, contextPath, queryIndex, mapperElement, dispatcherType);
        } else {
            includeRequest = ServletHttpIncludeRequest.newInstanceIncludeName(httpRequest, name, contextPath, dispatcherType);
        }

        // ServletHttpIncludeResponse. The class will prohibit operation data
        ServletHttpIncludeResponse includeResponse = new ServletHttpIncludeResponse(httpResponse);
        try {
            dispatch(includeRequest, includeResponse);
        } finally {
            recycle();
        }
    }

    public void dispatch(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }

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
        ServletHttpAsyncRequest asyncRequest = ServletHttpAsyncRequest.newInstanceAsyncPath(httpRequest, asyncContext, path, relativePathNoQueryString, contextPath, queryIndex, mapperElement);
        ServletHttpAsyncResponse asyncResponse = new ServletHttpAsyncResponse(httpResponse, outWrapper.unwrap());
        try {
            dispatch(asyncRequest, asyncResponse);
        } finally {
            recycle();
        }
    }

    public String getName() {
        if (filterChain == null) {
            return name;
        }
        return filterChain.getServletRegistration().getName();
    }

    public String getContextPath() {
        return contextPath;
    }

    public ServletFilterChain getFilterChain() {
        return filterChain;
    }

    public UrlMapper.Element<ServletRegistration> getMapperElement() {
        return mapperElement;
    }

    @Override
    public void recycle() {
        if (filterChain == null) {
            return;
        }
        filterChain.recycle();
        queryIndex = -1;
        contextPath = null;
        relativePathNoQueryString = null;
        path = null;
        name = null;
        mapperElement = null;
        filterChain = null;
        RECYCLER.recycleInstance(this);
    }

}
