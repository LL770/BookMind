package ai.bookmind.listener;

import ai.bookmind.service.BookUploadService;
import ai.bookmind.service.VectorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookProcessListener {

    private final BookUploadService bookUploadService;
    private final VectorizationService vectorizationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "bookmind.process.queue", ackMode = "MANUAL")
    public void handleProcessMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String msgBody = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            log.info("MQ 收到消息: {}", msgBody);

            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(msgBody, Map.class);
            String type = (String) msg.get("type");
            Number bookIdNum = (Number) msg.get("bookId");
            Number userIdNum = (Number) msg.get("userId");

            if (bookIdNum == null) {
                log.warn("MQ 消息缺少 bookId，丢弃: {}", msgBody);
                channel.basicAck(deliveryTag, false);
                return;
            }

            Long bookId = bookIdNum.longValue();
            Long userId = userIdNum != null ? userIdNum.longValue() : 0L;

            if ("parse".equals(type)) {
                bookUploadService.processBookFromQueue(bookId, userId);
            } else if ("vectorize".equals(type)) {
                bookUploadService.processVectorize(bookId, userId);
            } else if ("graph".equals(type)) {
                bookUploadService.processGraph(bookId, userId);
            } else {
                log.warn("未知消息类型: {}", type);
            }

            // 处理成功 → 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("MQ 消息处理完成: bookId={}, type={}", bookId, type);

        } catch (Throwable e) {
            log.error("MQ 消息处理失败: {}", msgBody, e);
            try {
                // 失败 → 死信队列（不重回队列避免死循环）
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ignored) {
            }
        }
    }
}
