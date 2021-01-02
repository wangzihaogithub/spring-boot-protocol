package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.servlet.util.ServletUtil;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Error page management
 * @author wangzihao
 */
public class ServletErrorPageManager {
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private boolean showErrorMessage = false;
    private final Map<String, ServletErrorPage> exceptionPages = new ConcurrentHashMap<>();
    private final Map<Integer, ServletErrorPage> statusPages = new ConcurrentHashMap<>();

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

    public boolean isShowErrorMessage() {
        return showErrorMessage;
    }

    public void setShowErrorMessage(boolean showErrorMessage) {
        this.showErrorMessage = showErrorMessage;
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
            if(throwable != null){
                logger.error("a unknown error. No error page handler", throwable.toString(), throwable);
            }
            return;
        }

        ServletHttpServletRequest request = ServletUtil.unWrapper(httpServletRequest);
        ServletHttpServletResponse response = ServletUtil.unWrapper(httpServletResponse);

        String errorPagePath = getErrorPagePath(request, errorPage);
        if (errorPagePath == null) {
            return;
        }
        ServletRequestDispatcher dispatcher = request.getRequestDispatcher(errorPagePath);
        try {
            response.resetBuffer();
        }catch (IllegalStateException e){
            logger.warn("stream close. not execute handleErrorPage. {}", Objects.toString(throwable,""),throwable);
            return;
        }
        if (dispatcher == null) {
            try {
                response.getWriter().write("not found ".concat(errorPagePath));
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException e) {
                logger.error("handleErrorPage() sendError. error={}",e.toString(),e);
            }
            return;
        }
        dispatcher.clearFilter();
        try {
            if(throwable != null) {
                httpServletRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, throwable);
                httpServletRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, throwable.getClass());
                if(isShowErrorMessage()) {
                    String localizedMessage = throwable.getLocalizedMessage();
                    if(localizedMessage == null){
                        StringWriter writer = new StringWriter();
                        throwable.printStackTrace(new PrintWriter(writer));
                        localizedMessage = writer.toString();
                    }
                    httpServletRequest.setAttribute(RequestDispatcher.ERROR_MESSAGE, localizedMessage);
                }
            }
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,dispatcher.getName());
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, response.getStatus());
            request.setDispatcherType(DispatcherType.ERROR);

            if (httpServletResponse.isCommitted()) {
                dispatcher.include(request, httpServletResponse);
            } else {
                response.resetHeader();
                response.resetBuffer(true);
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

    public static String getErrorPagePath(HttpServletRequest request, ServletErrorPage errorPage){
        String path = errorPage.getPath();
        if(path == null || path.isEmpty() ){
            return null;
        }
        if(!path.startsWith("/")){
            path =  "/".concat(path);
        }
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isEmpty()? path : contextPath.concat(path);
    }
}
