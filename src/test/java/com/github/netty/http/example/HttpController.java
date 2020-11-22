package com.github.netty.http.example;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Map;

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
    public String hello(String name, @RequestParam Map query,
//                        @RequestBody(required = false) Map body,
                        HttpSession session,Principal principal,
                        InputStream inputStream, OutputStream outputStream,
                        HttpServletRequest request,
                        HttpServletResponse response){
        return "hi! " + name;
    }

}
