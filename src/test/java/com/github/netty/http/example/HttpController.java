package com.github.netty.http.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@EnableScheduling
@RestController
@RequestMapping
public class HttpController {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Queue<MyDeferredResult<String>> queue = new ConcurrentLinkedQueue<>();

    /**
     * 访问地址： http://localhost:8080/test/hello
     * @param name name
     * @return hi! 小明
     */
    @RequestMapping("/hello")
    public String hello(String name){
        return "hi! " + name;
    }

    /**
     * 访问地址： http://localhost:8080/test/helloAsync
     * @param request netty request
     * @param response netty response
     * @return spring的延迟结果， 返回DeferredResult类型， spring就不会去做任何处理。
     *      这里的返回结果是在定时任务线程批量处理  {@link #on2000Delay}
     */
    @RequestMapping("/helloAsync")
    public DeferredResult<String> helloAsync(HttpServletRequest request,HttpServletResponse response){
        MyDeferredResult<String> deferredResult = new MyDeferredResult<>(request,response);
        queue.offer(deferredResult);
        return deferredResult;
    }

    /**
     * 2秒间隔的 定时任务
     * 异步批量处理
     */
    @Scheduled(fixedDelay = 2000)
    public void on2000Delay() {
        String data = "random value = " + UUID.randomUUID();
        MyDeferredResult<String> current;
        while ((current = queue.poll()) != null){
            current.setResult(data);
            logger.info("notify spring DeferredResult = {}",current.request.getRequestURL());
        }
    }

    /**
     * spring的延迟结果， 返回DeferredResult类型， spring就不会去做任何处理。
     * @param <T> 返回结果
     */
    public static class MyDeferredResult<T> extends DeferredResult<T> {
        public HttpServletRequest request;
        public HttpServletResponse response;
        public MyDeferredResult(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }
    }
}
