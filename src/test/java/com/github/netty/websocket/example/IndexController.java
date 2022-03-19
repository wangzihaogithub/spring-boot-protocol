package com.github.netty.websocket.example;

import com.github.netty.websocket.WebsocketBootstrap;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * test前端静态页面
 */
@Controller
@RequestMapping("/")
public class IndexController {

    @RequestMapping("/index.html")
    public ResponseEntity index() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "text/html;charset=utf-8");
        return new ResponseEntity<>(new InputStreamResource(WebsocketBootstrap.class.getResourceAsStream("/websocket/index.html")), headers, HttpStatus.OK);
    }
}
