package com.github.netty.javadubbo;

import com.github.netty.javadubbo.example.DemoAPI;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URL;

public class Client {

    private static final URL CONFIG_URL = Client.class.getResource(
            "/javadubbo/dubbo-client.xml");

    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext(CONFIG_URL.toString());
        classPathXmlApplicationContext.start();
        DemoAPI gphelloservice = (DemoAPI) classPathXmlApplicationContext.getBean("gphelloservice");
        while (true) {
            try {
                String response = gphelloservice.hello("测试", 2);
                System.out.println(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(1000);
        }
    }
}
