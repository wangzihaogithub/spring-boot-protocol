package com.github.netty.nrpc;

import com.github.netty.core.util.IOUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/**
 * rpc test
 * start app {@link NRpcClientBootstrap}
 * start app {@link NRpcServerBootstrap}
 * @author wangzihaogithub
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NRpcTests.class)
public class NRpcTests {

    @Test
    public void test() throws IOException {
        URL url = new URL("http://localhost:8081/sayHello?name=xiaowang");
        InputStream inputStream = url.openStream();
        String responseBody = IOUtil.readInput(inputStream);
        Assert.assertEquals("hi! xiaowang", responseBody);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int error = 0;
        int total = 0;
        while (true) {
            Thread.sleep(1000);
            URL url = new URL("http://localhost:8081/sayHello?name=xiaowang");
            try {
                InputStream inputStream = url.openStream();
                String responseBody = IOUtil.readInput(inputStream);
                if(!Objects.equals("hi! xiaowang", responseBody)){
                    error++;
                }
            }catch (IOException e){
                error++;
            }
            total++;
            System.out.println("total = " + total+", error = "+error);
        }
    }
}
