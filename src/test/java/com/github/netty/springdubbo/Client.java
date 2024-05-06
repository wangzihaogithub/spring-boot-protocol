package com.github.netty.springdubbo;

import com.github.netty.springdubbo.example.DemoAPI;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URL;

public class Client {

    private static final URL CONFIG_URL = Client.class.getResource(
            "/springdubbo/dubbo-client.xml");

    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext(CONFIG_URL.toString());
        classPathXmlApplicationContext.start();
        DemoAPI gphelloservice = (DemoAPI) classPathXmlApplicationContext.getBean("gphelloservice");
        while (true) {
            try {
                String response = gphelloservice.hello("测试",2);
                System.out.println(response);
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
