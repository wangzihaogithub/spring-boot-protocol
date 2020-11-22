package com.github.netty.http.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 支持异步单线程批量处理请求 {@link #queue} {@link #onDelay}
 * 适合浏览器后台进程的接口, 例如(IM消息场景, 表单定时自动保存等..)
 *
 * 请求可以在{@link #API_GROUP_BY_MS}毫秒内聚合，批量查询或批量写库。（演示代码用的是根据api名称聚合）
 *
 * 访问地址：（快速连续请求接口,可以看到效果）
 *  http://localhost:8080/test/insertOrder?productName=苹果
 *  http://localhost:8080/test/insertOrder?productName=香蕉
 *
 * @see #queue 这个队列存放{@link #API_GROUP_BY_MS}毫秒内的多个请求
 * @see #onDelay() 这个定时任务批量处理队列中的请求
 */
@EnableScheduling
@RestController
@RequestMapping
public class HttpGroupByApiController {
    /**
     * 接口聚合最大延迟时间 (等待毫秒内的请求批量读写)
     */
    private static final int API_GROUP_BY_MS = 3000;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Queue<MyDeferredResult<Map>> queue = new ConcurrentLinkedQueue<>();
    private final Map<String,String> databaseMap = new LinkedHashMap<>();{
        databaseMap.put("1","数据aaa");
        databaseMap.put("2","数据bbb");
        databaseMap.put("3","数据ccc");
    }

    /**
     * {@link #API_GROUP_BY_MS} 毫秒间隔的 定时任务
     * 异步批量处理
     */
    @Scheduled(fixedDelay = API_GROUP_BY_MS)
    public void onDelay() {
        String uuid = UUID.randomUUID().toString();

        Map<String, List<MyDeferredResult<Map>>> groupByApiMap = groupBy(queue);
        groupByApiMap.forEach((api, list)->{
            List<HttpServletRequest> requestList = list.stream()
                    .map(e-> e.request)
                    .collect(Collectors.toList());
            Map<String, String> batchQueryResult = map(api, requestList);
            for (MyDeferredResult<Map> e : list) {
                Map<String, Object> result = reduce(api, e.request, batchQueryResult);
                result.put("batchId",uuid);
                e.setResult(result);
                logger.info("notify spring DeferredResult = {}",e.request.getRequestURL());
            }
        });
    }

    /**
     * 请求可以在{@link #API_GROUP_BY_MS}毫秒内聚合，批量写入。
     *
     * 访问地址：（在 {@link #API_GROUP_BY_MS}毫秒内, 连续请求多次相同的接口。 ）
     *  http://localhost:8080/test/insertOrder?productName=香蕉
     *  http://localhost:8080/test/insertOrder?productName=苹果
     *
     * @param productName 商品名称. 香蕉
     * @param request netty request
     * @param response netty response
     * @return spring的延迟结果， 返回DeferredResult类型， spring就不会去做任何处理。
     *      这里的返回结果是在定时任务线程批量处理  {@link #onDelay}
     */
    @RequestMapping("/insertOrder")
    public DeferredResult<Map> insertOrder(String productName,HttpServletRequest request,HttpServletResponse response){
        MyDeferredResult<Map> deferredResult = new MyDeferredResult<>(request,response);
        queue.offer(deferredResult);
        return deferredResult;
    }

    /**
     * 请求可以在{@link #API_GROUP_BY_MS}毫秒内聚合，批量查查询。
     *
     * 访问地址：（在 {@link #API_GROUP_BY_MS}毫秒内, 连续请求多次相同的接口。 ）
     *  http://localhost:8080/test/helloAsyncUser?userId=1
     *  http://localhost:8080/test/helloAsyncUser?userId=2
     *
     *  http://localhost:8080/test/helloAsyncOrder?orderId=1
     *  http://localhost:8080/test/helloAsyncOrder?orderId=1
     *
     * @param userId userId
     * @param request netty request
     * @param response netty response
     * @return spring的延迟结果， 返回DeferredResult类型， spring就不会去做任何处理。
     *      这里的返回结果是在定时任务线程批量处理  {@link #onDelay}
     */
    @RequestMapping("/helloAsyncUser")
    public DeferredResult<Map> helloAsyncUser(String userId,HttpServletRequest request,HttpServletResponse response){
        MyDeferredResult<Map> deferredResult = new MyDeferredResult<>(request,response);
        queue.offer(deferredResult);
        return deferredResult;
    }

    @RequestMapping("/helloAsyncOrder")
    public DeferredResult<Map> helloAsyncOrder(String orderId,HttpServletRequest request,HttpServletResponse response){
        MyDeferredResult<Map> deferredResult = new MyDeferredResult<>(request,response);
        queue.offer(deferredResult);
        return deferredResult;
    }

    public Map<String,String> selectListByUserIdIn(List<HttpServletRequest> requestList){
        List<String> idList = requestList.stream()
                .map(request -> request.getParameter("userId"))
                .distinct()
                .collect(Collectors.toList());
        logger.info("sql = {}",String.format("select * from t_user where userId in (%s)", idList));
        return databaseMap;
    }

    public Map<String,String> selectListByOrderIdIn(List<HttpServletRequest> requestList){
        List<String> idList = requestList.stream()
                .map(request -> request.getParameter("orderId"))
                .distinct()
                .collect(Collectors.toList());
        logger.info("sql = {}",String.format("select * from t_order where orderId in (%s)", idList));
        return databaseMap;
    }

    public Map<String,String> insertList(List<HttpServletRequest> requestList){
        String values = requestList.stream()
                .map(request -> request.getParameter("productName"))
                .distinct()
                .collect(Collectors.joining("'),('","values ('","')"));
        logger.info("sql = insert into t_order (`product_name`) {}",values);
        return databaseMap;
    }

    private static Map<String, List<MyDeferredResult<Map>>> groupBy(Queue<MyDeferredResult<Map>> queue){
        MultiValueMap<String,MyDeferredResult<Map>> groupByMap = new LinkedMultiValueMap<>();
        MyDeferredResult<Map> current;
        while ((current = queue.poll()) != null){
            groupByMap.add(current.request.getRequestURI(),current);
        }
        return groupByMap;
    }

    /**
     * 做批量查询的 数据映射
     * @param api 按照接口聚合的某接口名称
     * @param requestList 所有这个接口的请求
     * @return 批量查询结果
     */
    private Map<String,String> map(String api, List<HttpServletRequest> requestList){
        //只查库一次。 比如：这是查数据库动作
        switch (api){
            //批量读
            case "/test/helloAsyncUser" :{
                return selectListByUserIdIn(requestList);
            }case "/test/helloAsyncOrder" :{
                return selectListByOrderIdIn(requestList);
            }case "/test/insertOrder" :{
                //批量写
                return insertList(requestList);
            }default:{
                return Collections.emptyMap();
            }
        }
    }

    private Map<String,Object> reduce(String api,HttpServletRequest request,Map<String,String> batchQueryResult){
        //每个请求,各自取各自的值。
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("api",api);
        switch (api){
            case "/test/helloAsyncUser" :{
                String id = request.getParameter("userId");
                result.put("id",id);
                result.put("result",batchQueryResult.get(id));
                break;
            }case "/test/helloAsyncOrder" :{
                String id = request.getParameter("orderId");
                result.put("id",id);
                result.put("result",batchQueryResult.get(id));
                break;
            }case "/test/insertOrder" :{
                String productName = request.getParameter("productName");
                result.put("productName",productName);
                result.put("result",true);
                break;
            }default:{}
        }
        return result;
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
