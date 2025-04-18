package com.github.netty.protocol.dubbo.packet;

import com.github.netty.protocol.dubbo.Body;

public class BodyEvent extends Body {

    private final Object event;

    public BodyEvent(Object event) {
        this.event = event;
    }

    public Object getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "BodyEvent{" +
                "\n\tevent=" + event +
                "\n}";
    }
}
