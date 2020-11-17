package com.github.netty.http.example;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

@EnableScheduling
@RestController
@RequestMapping
public class HttpController {

    /**
     * 访问地址： http://localhost:8080/test/hello
     * @param name name
     * @return hi! 小明
     */
    @RequestMapping("/hello")
    public String hello(String name, HttpServletRequest request){
        return "hi! " + name;
    }

}
