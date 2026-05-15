package ai.bookmind;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "ai.bookmind")
@MapperScan("ai.bookmind.mapper")
@EnableAsync
@EnableScheduling
@EnableRabbit
public class BookMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookMindApplication.class, args);
        System.out.println("BookMind Backend started successfully");
        System.out.println("Reader: http://localhost:8080");
        System.out.println("Knowledge Graph: http://localhost:8080/graph");
        System.out.println("AI Chat: http://localhost:8080/chat");
    }
}
