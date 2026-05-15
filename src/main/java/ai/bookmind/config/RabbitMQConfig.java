package ai.bookmind.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 */
@Configuration
public class RabbitMQConfig {

    /** 死信交换机 */
    @Bean
    public TopicExchange bookProcessDlx() {
        return new TopicExchange("bookmind.process.dlx");
    }

    /** 死信队列 */
    @Bean
    public Queue bookProcessDlq() {
        return QueueBuilder.durable("bookmind.process.dlq").build();
    }

    @Bean
    public Binding bookProcessDlqBinding(Queue bookProcessDlq, TopicExchange bookProcessDlx) {
        return BindingBuilder.bind(bookProcessDlq)
                .to(bookProcessDlx)
                .with("#");
    }

    /**
     * 书籍处理队列（失败消息→死信队列）
     */
    @Bean
    public Queue bookProcessQueue() {
        return QueueBuilder.durable("bookmind.process.queue")
                .withArgument("x-max-length", 10000)
                .withArgument("x-dead-letter-exchange", "bookmind.process.dlx")
                .withArgument("x-dead-letter-routing-key", "bookmind.process.dlq")
                .build();
    }

    /**
     * 书籍处理交换机
     */
    @Bean
    public TopicExchange bookProcessExchange() {
        return new TopicExchange("bookmind.process.exchange");
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding bookProcessBinding(Queue bookProcessQueue, TopicExchange bookProcessExchange) {
        return BindingBuilder.bind(bookProcessQueue)
                .to(bookProcessExchange)
                .with("bookmind.process.#");
    }

    /**
     * RabbitTemplate 配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * JSON消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
