package com.github.netty.register.servlet;

import com.github.netty.core.util.ExceptionUtil;
import com.github.netty.core.util.LoggerX;
import com.github.netty.register.servlet.util.ServletUtil;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误页管理
 * @author 84215
 */
public class ServletErrorPageManager {

    private LoggerX logger = new LoggerX(getClass());
    private Map<String, ServletErrorPage> exceptionPages = new ConcurrentHashMap<>();
    private Map<Integer, ServletErrorPage> statusPages = new ConcurrentHashMap<>();

    public void add(ServletErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType == null) {
            statusPages.put(errorPage.getStatus(), errorPage);
        } else {
            exceptionPages.put(exceptionType, errorPage);
        }
    }

    public void remove(ServletErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType == null) {
            statusPages.remove(errorPage.getStatus(), errorPage);
        } else {
            exceptionPages.remove(exceptionType, errorPage);
        }
    }

    public ServletErrorPage find(int statusCode) {
        return statusPages.get(statusCode);
    }

    public ServletErrorPage find(Throwable exceptionType) {
        if (exceptionType == null) {
            return null;
        }
        Class<?> clazz = exceptionType.getClass();
        String name = clazz.getName();
        while (!Object.class.equals(clazz)) {
            ServletErrorPage errorPage = exceptionPages.get(name);
            if (errorPage != null) {
                return errorPage;
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                break;
            }
            name = clazz.getName();
        }
        return null;
    }

    /**
     * 处理错误页
     * @param errorPage 错误页
     * @param throwable 错误
     * @param httpServletRequest 请求
     * @param httpServletResponse 响应
     */
    public void handleErrorPage(ServletErrorPage errorPage,Throwable throwable, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse){
        if(errorPage == null){
            return;
        }

        ServletHttpServletRequest request = ServletUtil.unWrapper(httpServletRequest);
        ServletHttpServletResponse response = ServletUtil.unWrapper(httpServletResponse);

        ServletRequestDispatcher dispatcher = request.getRequestDispatcher(errorPage.getPath());
        try {
            if(throwable != null) {
                httpServletRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, throwable.getClass());
            }
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,dispatcher.getName());
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, response.getStatus());
//            httpServletRequest.setAttribute(RequestDispatcher.ERROR_MESSAGE, response.getMessage());

            if (httpServletResponse.isCommitted()) {
                dispatcher.include(request, httpServletResponse);
            } else {
                response.resetBuffer(true);
                httpServletResponse.setContentLength(-1);
                dispatcher.forward(request, httpServletResponse);

                response.getOutputStream().setSuspendFlag(false);
            }
        } catch (Throwable e) {
            logger.error("on handleErrorPage error. url="+request.getRequestURL()+", case="+e.getMessage(),e);
            ExceptionUtil.handleThrowable(e);
        }
    }
}
