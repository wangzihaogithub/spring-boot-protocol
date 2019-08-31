package com.github.netty.protocol.servlet;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Locale;

/**
 * Servlet response introduction
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletHttpIncludeResponse extends HttpServletResponseWrapper {
    public ServletHttpIncludeResponse(HttpServletResponse response) {
        super(response);
    }

    /**
     * Disallow <code>reset()</code> calls on a included response.
     *
     * @exception IllegalStateException if the response has already
     *  been committed
     */
    @Override
    public void reset() {

    }


    /**
     * Disallow <code>setContentLength(int)</code> calls on an included
     * response.
     *
     * @param len The new content length
     */
    @Override
    public void setContentLength(int len) {

    }


    /**
     * Disallow <code>setContentLengthLong(long)</code> calls on an included
     * response.
     *
     * @param len The new content length
     */
    @Override
    public void setContentLengthLong(long len) {

    }


    /**
     * Disallow <code>setContentType()</code> calls on an included response.
     *
     * @param type The new content type
     */
    @Override
    public void setContentType(String type) {

    }


    /**
     * Disallow <code>setLocale()</code> calls on an included response.
     *
     * @param loc The new locale
     */
    @Override
    public void setLocale(Locale loc) {

    }


    /**
     * Ignore <code>setBufferSize()</code> calls on an included response.
     *
     * @param size The buffer size
     */
    @Override
    public void setBufferSize(int size) {

    }


    // -------------------------------------------- HttpServletResponse Methods


    /**
     * Disallow <code>addCookie()</code> calls on an included response.
     *
     * @param cookie The new cookie
     */
    @Override
    public void addCookie(Cookie cookie) {

    }


    /**
     * Disallow <code>addDateHeader()</code> calls on an included response.
     *
     * @param name The new header name
     * @param value The new header value
     */
    @Override
    public void addDateHeader(String name, long value) {

    }


    /**
     * Disallow <code>addHeader()</code> calls on an included response.
     *
     * @param name The new header name
     * @param value The new header value
     */
    @Override
    public void addHeader(String name, String value) {

    }


    /**
     * Disallow <code>addIntHeader()</code> calls on an included response.
     *
     * @param name The new header name
     * @param value The new header value
     */
    @Override
    public void addIntHeader(String name, int value) {

    }


    /**
     * Disallow <code>sendError()</code> calls on an included response.
     *
     * @param sc The new status code
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void sendError(int sc) throws IOException {

    }


    /**
     * Disallow <code>sendError()</code> calls on an included response.
     *
     * @param sc The new status code
     * @param msg The new message
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void sendError(int sc, String msg) throws IOException {

    }


    /**
     * Disallow <code>sendRedirect()</code> calls on an included response.
     *
     * @param location The new location
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void sendRedirect(String location) throws IOException {

    }

    /**
     * Disallow <code>setDateHeader()</code> calls on an included response.
     *
     * @param name The new header name
     * @param value The new header value
     */
    @Override
    public void setDateHeader(String name, long value) {

    }


    /**
     * Disallow <code>setHeader()</code> calls on an included response.
     *
     * @param name The new header name
     * @param value The new header value
     */
    @Override
    public void setHeader(String name, String value) {

    }


    /**
     * Disallow <code>setIntHeader()</code> calls on an included response.
     *
     * @param name The new header name
     * @param value The new header value
     */
    @Override
    public void setIntHeader(String name, int value) {

    }


    /**
     * Disallow <code>setStatus()</code> calls on an included response.
     *
     * @param sc The new status code
     */
    @Override
    public void setStatus(int sc) {

    }


    /**
     * Disallow <code>setStatus()</code> calls on an included response.
     *
     * @param sc The new status code
     * @param msg The new message
     * @deprecated As of version 2.1, due to ambiguous meaning of the message
     *             parameter. To set a status code use
     *             <code>setStatus(int)</code>, to send an error with a
     *             description use <code>sendError(int, String)</code>.
     */
    @Deprecated
    @Override
    public void setStatus(int sc, String msg) {

    }

    @Override
    public void setResponse(ServletResponse response) {
        super.setResponse(response);
    }

}
