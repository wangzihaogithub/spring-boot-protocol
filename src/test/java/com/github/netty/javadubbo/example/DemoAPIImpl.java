package com.github.netty.javadubbo.example;

public class DemoAPIImpl implements DemoAPI {
    @Override
    public String hello(String name) {
        System.out.println("name = " + name);
        return name + "service response hello!!";
    }
}
