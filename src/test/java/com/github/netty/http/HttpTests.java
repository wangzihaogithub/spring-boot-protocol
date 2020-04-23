package com.github.netty.http;

import com.github.netty.core.util.IOUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = HttpTests.class)
public class HttpTests {
    @Before
    public void init(){
        HttpBootstrap.main(new String[]{});
    }

    @Test
    public void test() throws IOException {
        URL url = new URL("http://localhost:8080/test/sayHello?name=xiaowang");
        InputStream inputStream = url.openStream();
        String responseBody = IOUtil.readInput(inputStream);
        Assert.assertEquals("hi! xiaowang",responseBody);
    }
}
