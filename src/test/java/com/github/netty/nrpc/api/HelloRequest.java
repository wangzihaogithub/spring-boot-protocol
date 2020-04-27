package com.github.netty.nrpc.api;

public class HelloRequest {
    private String name;
    private Integer id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    @Override
    public String toString() {
        return "Request{" +
                "name='" + name + '\'' +
                ", id=" + id +
                '}';
    }
}