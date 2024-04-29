package com.github.netty.javadubbo;

import com.github.netty.javadubbo.example.DemoAPI;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URL;

public class Client {

    private static final URL CONFIG_URL = Client.class.getResource(
            "/dubbo/dubbo-client.xml");

    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext(CONFIG_URL.toString());
        classPathXmlApplicationContext.start();
        DemoAPI gphelloservice = (DemoAPI) classPathXmlApplicationContext.getBean("gphelloservice");
        while (true) {
            try {
                RpcContext.getClientAttachment().setAttachment("remote.application", "order-service");
                String response = gphelloservice.hello("测试");
                System.out.println(response);
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
