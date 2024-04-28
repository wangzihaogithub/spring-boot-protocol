package com.github.netty.protocol.dubbo.packet;

import com.github.netty.protocol.dubbo.Body;

public class BodyFail extends Body {
    private final String errorMessage;

    public BodyFail(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
