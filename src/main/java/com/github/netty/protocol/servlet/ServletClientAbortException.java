package com.github.netty.protocol.servlet;

import java.io.IOException;

public final class ServletClientAbortException extends IOException {
    private static final long serialVersionUID = 1L;

    public ServletClientAbortException() {
    }

    public ServletClientAbortException(String message) {
        super(message);
    }

    public ServletClientAbortException(Throwable throwable) {
        super(throwable);
    }

    public ServletClientAbortException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
