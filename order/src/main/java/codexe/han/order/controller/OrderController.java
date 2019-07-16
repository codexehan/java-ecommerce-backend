package codexe.han.order.controller;

import codexe.han.order.client.InventoryClient;
import codexe.han.order.dto.OrderProductDTO;
import codexe.han.order.service.OrderService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
public class OrderController {

    private OrderService orderService;
    //proceed to checkout
    //need to login
    @PostMapping("/proceed/checkout")
    public ResponseEntity proceedCheckout(@RequestBody OrderProductDTO orderProductDTO){
        /**
         * 1.need to check login status
         * 2.need to check product validating time
         * 4.need to check whether can be delivered to the address
         * 3.need to check inventory
         *      enough: go to order page
         *      not enough: return error to remind
         */
        return proceedCheckout(orderProductDTO);
    }

    @PostMapping("/continue/checkout")
    public ResponseEntity continueCheckout(@RequestBody OrderProductDTO orderProductDTO){
        /**
         * 1.check inventory
         * 2.pre block the inventory
         *
         * @Transactional
         *      update db
         *      invalidate the redis
         * return true -> create order
         * 先更新数据库，再invalidate缓存。不能保证缓存和数据库的强一致性
         * 先invalidate缓存，再更新数据库。需要将read缓存和update数据库异步串行执行，来保证数据库的强一致性
         *
         *  NOTE: during peak load ->
         *      mq - update db
         *      sub quantity in redis
         *      先发送db 再减redis 防止少卖情况的发生
         */
        return null;

    }
}