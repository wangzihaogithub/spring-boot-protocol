package com.github.netty.protocol.servlet;

import com.github.netty.protocol.servlet.util.MediaType;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * Servlet asynchronous response, (note: control of the output stream is transferred to the new servlet, and the original servlet can no longer manipulate the output stream)
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletHttpAsyncResponse extends HttpServletResponseWrapper {
    private ServletHttpExchange servletHttpExchange;
    private ServletOutputStreamWrapper outWrapper = new ServletOutputStreamWrapper(null);;
    private PrintWriter writer;

    public ServletHttpAsyncResponse(ServletHttpServletResponse response, ServletOutputStream outputStream) {
        super(response);
        this.servletHttpExchange = response.getServletHttpExchange();
        this.outWrapper.wrap(outputStream);
    }

    @Override
    public ServletOutputStreamWrapper getOutputStream() throws IOException {
        return outWrapper;
    }

    @Override
    public void setBufferSize(int size) {

    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void reset() {
        checkCommitted();
        super.reset();
        if(outWrapper.unwrap() == null){
            return;
        }
        outWrapper.resetBuffer();
    }

    @Override
    public void resetBuffer() {
        checkCommitted();
        if(outWrapper.unwrap() == null){
            return;
        }
        outWrapper.resetBuffer();
    }

    @Override
    public void flushBuffer() throws IOException {
        getOutputStream().flush();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if(writer != null){
            return writer;
        }

        String characterEncoding = getCharacterEncoding();
        if(characterEncoding == null || characterEncoding.isEmpty()){
            if(MediaType.isHtmlType(getContentType())){
                characterEncoding = MediaType.DEFAULT_DOCUMENT_CHARACTER_ENCODING;
            }else {
                characterEncoding = servletHttpExchange.getServletContext().getResponseCharacterEncoding();
            }
            setCharacterEncoding(characterEncoding);
        }

        writer = new ServletPrintWriter(getOutputStream(),Charset.forName(characterEncoding));
        return writer;
    }

    @Override
    public void setResponse(ServletResponse response) {
        throw new UnsupportedOperationException("Unsupported Method On Forward setResponse ");
    }

    /**
     * Check the submission status
     * @throws IllegalStateException
     */
    private void checkCommitted() throws IllegalStateException {
        if(isCommitted()) {
            throw new IllegalStateException("Cannot perform this operation after response has been committed");
        }
    }

}
