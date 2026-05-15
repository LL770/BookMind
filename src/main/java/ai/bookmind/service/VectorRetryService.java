package ai.bookmind.service;

import ai.bookmind.entity.Book;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 启动时自动重试卡住的书籍
 * 书本解析完成（有章节）但向量化未完成的，重新发送 MQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorRetryService {

    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final RabbitTemplate rabbitTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void retryStuckBooks() {
        try {
            // 重试 status=1（解析完但向量化未完成）
            List<Book> stuck1 = bookMapper.selectByStatus(1);
            for (Book book : stuck1) {
                int chapterCount = chapterMapper.countByBookId(book.getId());
                log.info("发现卡住书籍(1) bookId={}, title={}, 章节数={}", book.getId(), book.getTitle(), chapterCount);

                if (chapterCount > 0) {
                    log.info("重新发送向量化消息 bookId={}", book.getId());
                    sendMq("vectorize", book.getId(), book.getUserId());
                } else {
                    log.info("重新发送解析消息 bookId={}", book.getId());
                    sendMq("parse", book.getId(), book.getUserId());
                }
            }

            // 重试 status=2（向量化完但知识图谱未完成）
            List<Book> stuck2 = bookMapper.selectByStatus(2);
            for (Book book : stuck2) {
                log.info("发现卡住书籍(2) bookId={}, title={}，重新发送图谱消息", book.getId(), book.getTitle());
                sendMq("graph", book.getId(), book.getUserId());
            }

            if (stuck1.isEmpty() && stuck2.isEmpty()) {
                log.info("未发现卡住的书籍");
            }
        } catch (Exception e) {
            log.error("重试卡住书籍失败", e);
        }
    }

    private void sendMq(String action, Long bookId, Long userId) {
        rabbitTemplate.convertAndSend("bookmind.process.exchange", "bookmind.process." + action,
                Map.of("bookId", bookId, "userId", userId));
    }
}
