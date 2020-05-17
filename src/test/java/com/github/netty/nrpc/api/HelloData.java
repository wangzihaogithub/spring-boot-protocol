package com.github.netty.nrpc.api;

public class HelloData {
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