package ai.bookmind.ai;

import ai.bookmind.entity.Book;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI 工具类 - 为ChatClient提供 FunctionCallback
 */
@Component
@RequiredArgsConstructor
public class BookTools {

    private final BookMapper bookMapper;
    private final NoteMapper noteMapper;
    private final BookService bookService;

    @Bean
    public FunctionCallback getMyNotesCallback() {
        Function<GetMyNotesRequest, List<Note>> fn = req -> {
            Long bookId = req.bookId();
            String keyword = req.keyword();
            List<Note> notes = noteMapper.selectByBookId(bookService.getCurrentUserId(), bookId);
            if (keyword != null && !keyword.isEmpty()) {
                notes = notes.stream()
                        .filter(note -> note.getContent().contains(keyword) ||
                                       note.getQuoteText().contains(keyword))
                        .toList();
            }
            return notes;
        };
        return FunctionCallback.builder()
                .function("getMyNotes", fn)
                .inputType(GetMyNotesRequest.class)
                .build();
    }

    @Bean
    public FunctionCallback getBookProgressCallback() {
        Function<GetBookProgressRequest, Integer> fn = req -> {
            Long userId = bookService.getCurrentUserId();
            return userId != null ? bookService.getReadingProgress(userId, req.bookId()) : 0;
        };
        return FunctionCallback.builder()
                .function("getBookProgress", fn)
                .inputType(GetBookProgressRequest.class)
                .build();
    }

    @Bean
    public FunctionCallback getBookDetailsCallback() {
        Function<GetBookDetailsRequest, Book> fn = req ->
            bookMapper.selectById(req.bookId());
        return FunctionCallback.builder()
                .function("getBookDetails", fn)
                .inputType(GetBookDetailsRequest.class)
                .build();
    }

    public record GetMyNotesRequest(Long bookId, String keyword) {}
    public record GetBookProgressRequest(Long bookId) {}
    public record VoidRequest() {}
    public record GetBookDetailsRequest(Long bookId) {}
}
