package com.github.netty.protocol.mysql.exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * ProxyException
 *
 * @author wangzihaogithub 2020-4-23 12:09:02
 */
public class ProxyException extends RuntimeException {
    public static final int ERROR_UNKOWN = 3000;
    public static final int ERROR_BACKEND_NO_CONNECTION = 3001;
    public static final int ERROR_BACKEND_CONNECT_FAIL = 3002;
    private int errorNumber = ERROR_UNKOWN;

    public ProxyException() {
        super();
    }

    public ProxyException(int errorNumber, String message) {
        super(message);
        this.errorNumber = errorNumber;
    }

    public ProxyException(int errorNumber, String message, Throwable cause) {
        super(message, cause, false, false);
        this.errorNumber = errorNumber;
    }

    public static String stackTraceToString(Throwable cause) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream pout = new PrintStream(out);
        cause.printStackTrace(pout);
        pout.flush();
        try {
            return new String(out.toByteArray());
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {
                // ignore as should never happen
            }
        }
    }

    public int getErrorNumber() {
        return errorNumber;
    }

    public void setErrorNumber(int errorNumber) {
        this.errorNumber = errorNumber;
    }

    @Override
    public String toString() {
        String s = getClass().getSimpleName();
        String message = getLocalizedMessage();
        return (message != null) ? (s + ": " + message) : s;
    }

}
