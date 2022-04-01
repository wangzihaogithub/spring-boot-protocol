package com.github.netty.http;

import com.github.netty.core.util.IOUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

@SpringBootTest(classes = HttpTests.class)
public class HttpTests {
    @Test
    public void test() throws IOException {
        URL url = new URL("http://localhost:8080/test/hello?name=xiaowang");
        InputStream inputStream = url.openStream();
        String responseBody = IOUtil.readInput(inputStream);
        Assert.isTrue(Objects.equals("hi! xiaowang", responseBody));
    }
}
