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
 *
 * @author wangzihao
 */
public class ServletErrorPageManager {
    private static final LoggerX logger = LoggerFactoryX.getLogger(ServletErrorPageManager.class);
    private final Map<String, ServletErrorPage> exceptionPages = new ConcurrentHashMap<>();
    private final Map<Integer, ServletErrorPage> statusPages = new ConcurrentHashMap<>();
    private boolean showErrorMessage = false;

    public static void printThrowable(Throwable throwable, ServletRequestDispatcher requestDispatcher) {
        if (throwable == null) {
            return;
        }
        if (requestDispatcher == null) {
            logger.warn("a unknown error! ", throwable);
        } else if (requestDispatcher.getFilterChain().isFilterEnd()) {
            logger.warn("Servlet.service() for servlet [{}] threw exception",
                    requestDispatcher.getName(), throwable);
        } else {
            logger.warn("Filter.doFilter() for filter [{}] threw exception",
                    requestDispatcher.getFilterChain().getFilterRegistration().getName(), throwable);
        }
    }

    public static String getErrorPagePath(HttpServletRequest request, ServletErrorPage errorPage) {
        String path = errorPage.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (!path.startsWith("/")) {
            path = "/".concat(path);
        }
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isEmpty() ? path : contextPath.concat(path);
    }

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
     *
     * @param errorPage           errorPage
     * @param throwable           throwable
     * @param requestDispatcher   requestDispatcher
     * @param httpServletRequest  httpServletRequest
     * @param httpServletResponse httpServletResponse
     */
    public void handleErrorPage(ServletErrorPage errorPage, Throwable throwable, ServletRequestDispatcher requestDispatcher, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        printThrowable(throwable, requestDispatcher);

        if (errorPage == null) {
            if (throwable != null) {
                try {
                    httpServletResponse.setCharacterEncoding("utf-8");
                    httpServletResponse.setContentType("text/html");
                    httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    httpServletResponse.getWriter().write("<!DOCTYPE html>\n" +
                            "<html lang=\"en\">\n" +
                            "<head>\n" +
                            "    <meta charset=\"UTF-8\">\n" +
                            "    <title>a unknown error!</title>\n" +
                            "</head>\n" +
                            "<body>\n" +
                            "<p>" + throwable + "</p>\n" +
                            "</body>\n" +
                            "</html>");
                } catch (IOException ignored) {
                }
            }
            return;
        }

        ServletHttpServletRequest request = ServletUtil.unWrapper(httpServletRequest);
        ServletHttpServletResponse response = ServletUtil.unWrapper(httpServletResponse);
        if (!request.getHttpExchange().getChannelHandlerContext().channel().isActive()) {
            return;
        }

        String errorPagePath = getErrorPagePath(request, errorPage);
        if (errorPagePath == null) {
            return;
        }
        ServletRequestDispatcher dispatcher = request.getServletContext().getRequestDispatcher(errorPagePath, DispatcherType.ERROR);
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            logger.warn("stream close. not execute handleErrorPage. {}", Objects.toString(throwable, ""), throwable);
            return;
        }
        if (dispatcher == null) {
            try {
                response.getWriter().write("not found ".concat(errorPagePath));
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException e) {
                logger.warn("handleErrorPage() sendError. error={}", e.toString(), e);
            }
            return;
        }
        try {
            if (throwable != null) {
                httpServletRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, throwable);
                httpServletRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, throwable.getClass());
                if (isShowErrorMessage()) {
                    String localizedMessage = throwable.getLocalizedMessage();
                    if (localizedMessage == null) {
                        StringWriter writer = new StringWriter();
                        throwable.printStackTrace(new PrintWriter(writer));
                        localizedMessage = writer.toString();
                    }
                    httpServletRequest.setAttribute(RequestDispatcher.ERROR_MESSAGE, localizedMessage);
                }
            }
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, dispatcher.getName());
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
            httpServletRequest.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, response.getStatus());
            request.setDispatcherType(DispatcherType.ERROR);

            if (httpServletResponse.isCommitted()) {
                dispatcher.include(request, httpServletResponse, DispatcherType.ERROR);
            } else {
                response.resetHeader();
                response.resetBuffer(true);
                dispatcher.forward(request, httpServletResponse, DispatcherType.ERROR);

                response.getOutputStream().setSuspendFlag(false);
            }
        } catch (Throwable e) {
            logger.warn("on handleErrorPage error. url=" + request.getRequestURL() + ", case=" + e.getMessage(), e);
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
