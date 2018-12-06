# Spring-boot-protocol
Springboot协议扩展包, 允许单端口提供多协议服务.其中内置多种网络传输(标准与规范)的实现库, 轻松添加或扩展协议. 例: HttpServlet, RPC, MQTT, FTP, DNS.

    1.可以替代tomcat或jetty. 导包后一个注解即用. 
    
    2.HttpServlet性能比tomcat的NIO高出 20%(QPS)
    
    3.RPC性能略胜阿里巴巴的Dubbo, 使用习惯保持与springcloud相同, 可以不改springcloud代码直接切换RPC
    
    4.MQTT等物联网协议可以在不依赖协议网关, 单机同时支持N种协议 (例: HTTP,MQTT,FTP,DNS. 底层原理是,接到数据包后,进行协议路由.)
    
    5.可以添加自定义传输协议. (例: 定长传输, 分隔符传输)
    
    6.高并发下服务器内存依然保持平稳

作者邮箱 : 842156727@qq.com

github地址 : https://github.com/wangzihaogithub


### 使用方法

#### 1.添加依赖, 在pom.xml中加入 （注: 1.x.x版本是用于springboot1.0，2.x.x版本用于springboot2.0）

    <dependency>
      <groupId>com.github.wangzihaogithub</groupId>
      <artifactId>spring-boot-protocol</artifactId>
      <version>2.0.0</version>
    </dependency>
	
	
#### 2.开启netty容器

    @EnableNettyServletEmbedded//切换容器的注解
    @SpringBootApplication
    public class ExampleServletApplication {
    
        public static void main(String[] args) {
            SpringApplication.run(ExampleServletApplication.class, args);
        }
    }

#### 3.完成!

    2018-11-13 19:29:46.176  INFO 17544 --- [           main] c.g.n.e.s.ExampleServletApplication      : Started ExampleServletApplication in 1.847 seconds (JVM running for 2.988)
    2018-11-13 19:29:46.424  INFO 17544 --- [ettyTcpServer@1] c.g.netty.springboot.NettyTcpServer      : NettyTcpServer@1 start [port = 10002, os = windows 10, pid = 17544]...
