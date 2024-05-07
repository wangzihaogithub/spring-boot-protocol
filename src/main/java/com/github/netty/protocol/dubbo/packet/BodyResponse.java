package com.github.netty.protocol.dubbo.packet;

import com.github.netty.protocol.dubbo.Body;

import java.util.Map;

public class BodyResponse extends Body {
    private final Object value;
    private final Object throwable;
    private final Map<String, Object> attachments;

    public BodyResponse(Object value, Object throwable, Map<String, Object> attachments) {
        this.value = value;
        this.throwable = throwable;
        this.attachments = attachments;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public Object getValue() {
        return value;
    }

    public Object getThrowable() {
        return throwable;
    }

    @Override
    public String toString() {
        return "BodyResponse{" +
                "\n\tvalue=" + value +
                ",\n\tthrowable=" + throwable +
                ",\n\tattachments=" + attachments +
                "\n}";
    }
}
