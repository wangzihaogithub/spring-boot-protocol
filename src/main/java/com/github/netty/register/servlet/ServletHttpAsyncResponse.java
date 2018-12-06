package com.github.netty.register.servlet;

import com.github.netty.register.servlet.util.MediaType;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * servlet异步响应, (注:输出流的控制权转移给新servlet, 原始servlet将不能再操作输出流)
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 *
 * @author acer01
 *  2018/7/15/015
 */
@sun.misc.Contended
public class ServletHttpAsyncResponse extends HttpServletResponseWrapper {

    private ServletHttpObject httpServletObject;
    private ServletOutputStreamWrapper outWrapper = new ServletOutputStreamWrapper(null);;
    private PrintWriter writer;

    public ServletHttpAsyncResponse(ServletHttpServletResponse response, ServletOutputStream outputStream) {
        super(response);
        this.httpServletObject = response.getHttpServletObject();
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
                characterEncoding = httpServletObject.getServletContext().getResponseCharacterEncoding();
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
     * 检查提交状态
     * @throws IllegalStateException
     */
    private void checkCommitted() throws IllegalStateException {
        if(isCommitted()) {
            throw new IllegalStateException("Cannot perform this operation after response has been committed");
        }
    }

}
