package com.github.netty.protocol.servlet;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Error page
 * @author wangzihao
 */
public class ServletErrorPage {
    private final int status;
    private final Class<? extends Throwable> exception;
    private final String path;

    public ServletErrorPage(int status, Class<? extends Throwable> exception, String path) {
        this.status = status;
        this.exception = exception;
        try {
            this.path = URLDecoder.decode(path,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public int getStatus() {
        return status;
    }

    public Class<? extends Throwable> getException() {
        return exception;
    }

    public String getPath() {
        return path;
    }

    public String getExceptionName() {
        return (this.exception != null) ? this.exception.getName() : null;
    }

    public boolean isGlobal() {
        return (this.status == 0 && this.exception == null);
    }

    public String getExceptionType() {
        return (this.exception != null) ? this.exception.getName() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServletErrorPage)) {
            return false;
        }

        ServletErrorPage errorPage = (ServletErrorPage) o;
        return status == errorPage.status && (exception != null ? exception.equals(errorPage.exception) : errorPage.exception == null) && (path != null ? path.equals(errorPage.path) : errorPage.path == null);
    }

    @Override
    public int hashCode() {
        int result = status;
        result = 31 * result + (exception != null ? exception.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServletErrorPage{" +
                "status=" + status +
                ", exception=" + exception +
                ", path='" + path + '\'' +
                '}';
    }
}
