package codexe.han.order.service.Impl;

import codexe.han.common.response.CodexeApi;
import codexe.han.order.client.InventoryClient;
import codexe.han.order.common.BlockInventoryStatus;
import codexe.han.order.dto.OrderProductDTO;
import codexe.han.order.repository.OrderItemRepository;
import codexe.han.order.repository.OrderRepository;
import codexe.han.order.service.ClientService;
import codexe.han.order.service.OrderService;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;


@Service
@AllArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private InventoryClient inventoryClient;

    private OrderRepository orderRepository;

    private OrderItemRepository orderItemRepository;

    private ClientService clientService;

    @Override
    public Object proceedCheckout(OrderProductDTO orderProductDTO) {
        ResponseEntity<CodexeApi> validateResponse = this.inventoryClient.checkInventory(orderProductDTO.getInventoryId(), orderProductDTO.getQuantity());
        if((boolean)validateResponse.getBody().getData()){
            //return the check out page
            return "continue check out page";
        }
        else{
            return "no enough inventory";
        }
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public Object continueCheckout(OrderProductDTO orderProductDTO) {
        /**
         * 扣减库存业务，尽量不要轻易放弃，因为到了这一步，用户是真心想买的。。
         *
         * 1.先减库存 后生成订单，对账系统校验扣减库存日志 =》一旦订单生成失败，对账系统也很难去校验，除非是将order_id一起记录
         * 2.先生成订单，后减库存，对账系统校验订单日志 =》一旦订单生成失败，直接不需要进行库存扣减，
         *      A.如果扣减库存超时，对账系统提取扫描订单，检测库存是否扣减成功
         *      B.库存不足，这个时候需要更新cart商品状态，并检测其他商品状态
         *      C.库存足够，这个时候需要从cart移除（更新状态）
         *  缺点就是需要对订单表进行多次更新 对数据库进行多次写入
         */
        int PROCESSING = 1000;
        int TO_BE_PAIED = 2000;
        int EXPIRED = 3000;
        int PAIED = 4000;
        int DELIVERYING = 5000;
        int DELIVERED = 6000;
        int REFUND = 7000;
        int COMPLETED = 8000;
        int orderStatus = PROCESSING;//待处理; state machine 有利于解耦
        long orderId = 123L;//分布式订单id的生成

        BlockInventoryStatus resStatus = this.clientService.blockInventorySync(orderProductDTO.getCartItemId(), orderId,orderProductDTO.getInventoryId(),orderProductDTO.getQuantity());
        switch (resStatus){
            case SUCCESS:
                /**
                 * 库存足够，生成订单
                 * orderId需要用算法生成，有一定的讲究，比如 snowflake算法
                 * status=1000表示初始状态，等待处理
                 *
                 * 对账系统会查看
                 */
                log.info("扣减库存成功");
                orderStatus = TO_BE_PAIED;//库存足够，下单成功，待付款
                break;
            case FAILED:
                log.info("扣减库存失败");
                /**
                 * 库存不足，更新cart状态，mq异步
                 */
                break;
            case ASYNC:
                log.info("扣减系统繁忙，异步扣减，用户导向排队页面");
                orderStatus = PROCESSING;
                break;
            case SYSTEM_ISSUE:
                log.info("mq system timeout issue");
                orderStatus = PROCESSING;
        }
        this.orderRepository.insert(orderId, orderProductDTO.getCustomerId(), orderStatus);
        this.orderItemRepository.insert(orderId,
                orderProductDTO.getCustomerId(),
                orderProductDTO.getProductId(),
                orderProductDTO.getInventoryId(),
                orderProductDTO.getCartItemId(),
                orderProductDTO.getQuantity());
        /**
         * 更改cart 商品状态（下单成功，购物车移除），mq异步
         */
        return null;
    }

    /**
     * 这种方式会导致
     */
    @HystrixCommand
    @Override
    @Async//hystrix 异步执行
    public void testHystrixCommand(DeferredResult deferredResult) {
        log.info("{}进入testHystrixCommand in order service",Thread.currentThread().getName());
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        deferredResult.setResult("done");
        log.info("{}退出testHystrixCommand in order service",Thread.currentThread().getName());
    }

}
