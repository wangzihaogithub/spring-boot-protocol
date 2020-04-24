package com.github.netty.http.example;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HttpController {

    @RequestMapping("/sayHello")
    public String sayHello(String name){
        return "hi! " + name;
    }

}
