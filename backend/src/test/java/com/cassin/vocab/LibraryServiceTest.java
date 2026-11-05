package com.cassin.vocab;

import static com.cassin.vocab.VocabFixtures.sense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.LibraryWord;

/**
 * Thư viện từ: LibraryService chỉ làm 3 việc, mỗi việc một test.
 *   ① ép page / size, q null → ""      ② gắn 2 cờ cho từng từ của trang      ③ trang rỗng thì không hỏi cờ
 * Câu JPQL không chạy ở đây (repository là mock): Spring đã kiểm tra cú pháp lúc app khởi động.
 */
@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {
  private static final Long USER_ID = 1L;

  @Mock WordRepository wordRepository;
  @InjectMocks LibraryService libraryService;

  // Từ hệ thống (owner = null). Nghĩa có id = id từ × 10 + vị trí (xem VocabFixtures).
  private final Word light = VocabFixtures.word(1L, "light", null,
      sense(PartOfSpeech.NOUN, "ánh sáng"), sense(PartOfSpeech.ADJECTIVE, "nhẹ"));
  private final Word tackle = VocabFixtures.word(3L, "tackle", null, sense(PartOfSpeech.VERB, "giải quyết"));

  @Test
  @DisplayName("Gắn đúng 2 cờ cho từng từ, kèm nghĩa, giữ nguyên số trang / tổng số")
  void search_mapsFlags() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(wordRepository.findLibrary(USER_ID, "", false, pageable))
        .thenReturn(new PageImpl<>(List.of(light, tackle), pageable, 22));
    when(wordRepository.findInOwnDecks(USER_ID, List.of(1L, 3L))).thenReturn(List.of(3L));
    when(wordRepository.findStudiedWords(USER_ID, List.of(1L, 3L))).thenReturn(List.of(1L));

    PageResponse<LibraryWord> result = libraryService.search(USER_ID, null, false, 0, 20);

    assertThat(result.content())
        .extracting(LibraryWord::term, LibraryWord::inMyDecks, LibraryWord::studying)
        .containsExactly(
            tuple("light", false, true),     // đang học, chưa đặt vào bộ nào của tôi
            tuple("tackle", true, false));   // đã đặt vào bộ, chưa học
    assertThat(result.content().get(0).senses()).extracting(s -> s.definition()).containsExactly("ánh sáng", "nhẹ");
    assertThat(result.totalElements()).isEqualTo(22);
  }

  @Test
  @DisplayName("q được cắt khoảng trắng, onlyNew chuyển nguyên xuống repository")
  void search_passesQueryAndFilter() {
    when(wordRepository.findLibrary(eq(USER_ID), eq("li"), eq(true), any(Pageable.class)))
        .thenReturn(Page.empty());

    libraryService.search(USER_ID, "  li ", true, 0, 20);

    verify(wordRepository).findLibrary(eq(USER_ID), eq("li"), eq(true), any(Pageable.class));
  }

  @Test
  @DisplayName("Trang rỗng: không chạy 2 câu hỏi cờ (tránh in () rỗng)")
  void search_emptyPage_skipsFlagQueries() {
    when(wordRepository.findLibrary(anyLong(), anyString(), anyBoolean(), any(Pageable.class)))
        .thenReturn(Page.empty());

    PageResponse<LibraryWord> result = libraryService.search(USER_ID, "zzz", false, 0, 20);

    assertThat(result.content()).isEmpty();
    verify(wordRepository, never()).findInOwnDecks(anyLong(), any());
    verify(wordRepository, never()).findStudiedWords(anyLong(), any());
  }

  @Test
  @DisplayName("size quá lớn bị ép về 100, page âm về 0")
  void search_clampsPaging() {
    when(wordRepository.findLibrary(anyLong(), anyString(), anyBoolean(), any(Pageable.class)))
        .thenReturn(Page.empty());

    libraryService.search(USER_ID, "x", true, -1, 999);

    verify(wordRepository).findLibrary(USER_ID, "x", true, PageRequest.of(0, LibraryService.MAX_PAGE_SIZE));
  }
}
