package com.github.netty.http;

import com.github.netty.core.util.LinkedMultiValueMap;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.servlet.http.Cookie;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import static com.github.netty.protocol.servlet.util.ServletUtil.*;

@EnableNettyEmbedded
@SpringBootApplication
public class HttpBootstrap {
    private static final URL CONFIG_URL = HttpBootstrap.class.getResource(
            "/http/application.yaml");

    public static void main(String[] args) {
        Cookie[] cookies = decodeCookie("1");
        String dateByRfcHttp = getDateByRfcHttp();

        LinkedMultiValueMap<String, String> objectObjectLinkedMultiValueMap = new LinkedMultiValueMap<>();
        decodeByUrl(objectObjectLinkedMultiValueMap, "ew?qwq="+ URLEncoder.encode("s深深的 辅导"), Charset.defaultCharset());
        System.out.println("objectObjectLinkedMultiValueMap = " + objectObjectLinkedMultiValueMap);

//        NettyReportRunnable.start();
        System.getProperties().put("spring.config.location", CONFIG_URL.toString());
        SpringApplication.run(HttpBootstrap.class, args);
    }

}
