package com.github.netty.javadubbo.example;

public class DemoAPIImpl implements DemoAPI {
    @Override
    public String hello(String name, int w) {
        System.out.println("name = " + name + "w" + w);
        return name + "service response hello!!";
    }
}
