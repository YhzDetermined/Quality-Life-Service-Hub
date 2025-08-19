package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;

@Service
@Slf4j
public class OrderMessageConsumer {
    @Resource
    private final RetryTemplate retryTemplate;
    @Resource
    private IVoucherOrderService voucherOrderService;

    public OrderMessageConsumer(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }

    @RabbitListener(queues = OrderQueueConfiguration.ORDER_QUEUE)
    public void handleOrder(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        VoucherOrder order = null;
        try {
            // 反序列化消息
            order = (VoucherOrder) new Jackson2JsonMessageConverter().fromMessage(message);
            log.info("接收到消息" + order);
            // Spring Retry 包裹业务逻辑
            VoucherOrder finalOrder = order;
            retryTemplate.execute(context -> {
                voucherOrderService.handleVoucherOrder(finalOrder);
                return null;
            });

            // 成功 → ACK
            channel.basicAck(deliveryTag, false);
            log.info("✅ 订单处理成功并ACK: " + order);

        } catch (Exception e) {
            log.error("❌ 订单重试3次失败，进入死信队列: " + order + "，异常：" + e.getMessage());

            // 失败 → NACK 并进入死信队列
            channel.basicNack(deliveryTag, false, false);
        }
    }
    @RabbitListener(queues = OrderQueueConfiguration.DLX_QUEUE)
    public void handleDeadLetter(VoucherOrder order) {
        System.err.println("💀 死信队列收到订单: " + order);
        // TODO: 补偿逻辑（写数据库、报警通知、人工处理）
    }

}
