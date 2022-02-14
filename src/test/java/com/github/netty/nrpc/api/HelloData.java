package com.github.netty.nrpc.api;

import java.io.Serializable;

public class HelloData implements Serializable {
    private String say;

    public String getSay() {
        return say;
    }

    public void setSay(String say) {
        this.say = say;
    }

    @Override
    public String toString() {
        return "Response{" +
                "say='" + say + '\'' +
                '}';
    }
}