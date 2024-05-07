package com.github.netty.protocol.dubbo.packet;

import com.github.netty.protocol.dubbo.Body;

public class BodyHeartBeat extends Body {

    private final Object event;

    public BodyHeartBeat(Object event) {
        this.event = event;
    }

    public Object getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "BodyHeartBeat{" +
                "\n\tevent=" + event +
                "\n}";
    }
}
