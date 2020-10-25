# Spring-boot-protocol

将springboot的WebServer改为了NettyTcpServer, 为使用者扩充了网络编程的能力.

![](https://user-images.githubusercontent.com/18204507/68989252-9d871a80-087e-11ea-96e1-20c12689c12a.png)

多协议服务器, Springboot协议扩展包, 允许单端口提供多协议服务.其中内置多种网络传输(标准与规范)的实现库, 轻松添加或扩展协议. 例: HttpServlet, RPC, MQTT（物联网通讯协议）, Websocket, RTSP(流媒体协议), DNS（域名解析协议）,MYSQL协议.

    1.可以替代tomcat或jetty. 导包后一个@EnableNettyEmbedded注解即用. 
    
    2.支持异步http请求聚合后用 select * from id in (httpRequestList). 示例：com.github.netty.http.example.HttpGroupByApiController.java
    
    3.支持异步零拷贝。sendFile, mmap. 示例：com.github.netty.http.example.HttpZeroCopyController.java
    
    4.HttpServlet性能比tomcat的NIO高出 20%(TPS)
    
    5.RPC性能略胜阿里巴巴的Dubbo, 使用习惯保持与springcloud相同, 可以不改springcloud代码替换Feign调用
    
    6.Mysql,MQTT等协议可以在不依赖协议网关, 单机单端口同时支持N种协议 (例: HTTP,MQTT,Mysql,RTSP,DNS. 底层原理是,接到数据包后,进行协议路由.)
    
    7.可以添加自定义传输协议. (例: 定长传输, 分隔符传输)
    
    8.高并发下服务器内存抖动在10M左右。 tomcat抖动在500M左右。

作者邮箱 : 842156727@qq.com

github地址 : https://github.com/wangzihaogithub


如果需要不依赖spring的servlet, 可以使用 https://github.com/wangzihaogithub/netty-servlet (支持文件零拷贝,可扩展底层通讯)


### 使用方法

#### 1.添加依赖, 在pom.xml中加入 （注: 1.x.x版本是用于springboot1.0，2.x.x版本用于springboot2.0）

    <!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/spring-boot-protocol -->
    <dependency>
      <groupId>com.github.wangzihaogithub</groupId>
      <artifactId>spring-boot-protocol</artifactId>
      <version>2.0.12</version>
    </dependency>

	
#### 2.开启netty容器

    @EnableNettyEmbedded//切换容器的注解
    @SpringBootApplication
    public class ExampleApplication {
    
        public static void main(String[] args) {
            SpringApplication.run(ExampleApplication.class, args);
        }
    }

#### 3.启动, 已经成功替换tomcat, 切换至 NettyTcpServer!
	2019-02-28 22:06:16.192  INFO 9096 --- [er-Boss-NIO-2-1] c.g.n.springboot.server.NettyTcpServer   : NettyTcpServer@1 start (port = 10004, pid = 9096, protocol = [my-protocol, http, nrpc, mqtt], os = windows 8.1) ...
	2019-02-28 22:06:16.193  INFO 9096 --- [           main] c.g.example.ProtocolApplication10004     : Started ProtocolApplication10004 in 2.508 seconds (JVM running for 3.247)    
---

#### 更多功能例子example-> [请点击这里查看示例代码](https://github.com/wangzihaogithub/netty-example "https://github.com/wangzihaogithub/netty-example")

##### 示例1. 编写并添加一个自定义传输协议

##### 示例2. springcloud中替换Feign的调用方式

##### 示例3. 对springboot-websocket的支持

##### 示例4. 协议网关, 监控各个协议的流量

 ---

#### 核心代码

com.github.netty.springboot.server.NettyTcpServer服务器启动时

com.github.netty.protocol.DynamicProtocolChannelHandler 接收新链接的第一个TCP数据包进行路由

com.github.netty.core.ProtocolHandler 处理之后的数据交换逻辑


#### 如何参与

* 有问题交issue, 想改代码直接pull request即可. github都会通过微信及时通知我.

* 有不懂得地方,我都会及时回复.

* 如果觉得这个产品还不错，请多多向您的朋友、同事推荐，感谢至极


http://alios.cn

http://liteos.com

http://rt-thread.org


