package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.servlet.util.ServletUtil;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Error page management
 * @author wangzihao
 */
public class ServletErrorPageManager {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
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
     * Handle error page
     * @param errorPage errorPage
     * @param throwable throwable
     * @param httpServletRequest httpServletRequest
     * @param httpServletResponse httpServletResponse
     */
    public void handleErrorPage(ServletErrorPage errorPage, Throwable throwable, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse){
        if(errorPage == null){
            return;
        }

        if(throwable != null) {
            logger.error(throwable.getMessage(), throwable);
        }
        ServletHttpServletRequest request = ServletUtil.unWrapper(httpServletRequest);
        ServletHttpServletResponse response = ServletUtil.unWrapper(httpServletResponse);
        ServletRequestDispatcher dispatcher = request.getRequestDispatcher(errorPage.getPath());
        if (dispatcher == null) {
            try {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException e) {
                logger.error("sendError. error={}",e.toString(),e);
            }
            return;
        }
        dispatcher.clearFilter();
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
            if (e instanceof ThreadDeath) {
                throw (ThreadDeath) e;
            }
            if (e instanceof StackOverflowError) {
                return;
            }
            if (e instanceof VirtualMachineError) {
                throw (VirtualMachineError) e;
            }
        }
    }
}
