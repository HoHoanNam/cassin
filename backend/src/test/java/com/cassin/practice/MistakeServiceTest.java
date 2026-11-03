package com.cassin.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.practice.dto.MistakeItem;
import com.cassin.vocab.PartOfSpeech;

/**
 * Sổ lỗi sai: MistakeService chỉ có 3 việc, mỗi việc một nhóm test.
 *   ① kiểm tra days (1–30)      ② tính since bằng Clock      ③ ép limit vào [1, 50]
 *
 * Unit test KHÔNG chạy database: ReviewEventRepository là MOCK (đồ giả do Mockito tạo).
 * Ta chỉ kiểm tra service gọi repository với ĐÚNG tham số, và xử lý kết quả đúng.
 * Câu JPQL thì đã được Spring kiểm tra lúc app khởi động.
 */
@ExtendWith(MockitoExtension.class)
class MistakeServiceTest {
  private static final Long USER_ID = 1L;

  // "Bây giờ" cố định: 03/11/2026 13:00 giờ Việt Nam = 06:00 UTC.
  // Nhờ Clock cố định, test chạy hôm nay hay năm sau đều cho cùng một kết quả.
  private static final Instant NOW = Instant.parse("2026-11-03T06:00:00Z");

  @Mock ReviewEventRepository eventRepository;

  private MistakeService mistakeService;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    // Tạo service bằng tay để truyền Clock cố định vào (thứ tự = thứ tự field final trong MistakeService).
    mistakeService = new MistakeService(eventRepository, fixedClock);
  }

  // ===================== Trường hợp bình thường =====================

  @Test
  @DisplayName("Mặc định 7 ngày / 20 dòng: đúng userId, since = 7 ngày trước, trả nguyên kết quả repository")
  void list_default_passesCorrectArguments() {
    MistakeItem improve = new MistakeItem(20L, 2L, "improve", PartOfSpeech.VERB, "cải thiện", 3L,
        Instant.parse("2026-11-02T02:15:00Z"));
    // any(): chấp nhận mọi giá trị. Lát nữa dùng ArgumentCaptor để xem giá trị THẬT đã được truyền.
    when(eventRepository.findMistakes(eq(USER_ID), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(improve));

    List<MistakeItem> result = mistakeService.list(USER_ID, 7, 20);

    // ArgumentCaptor = "máy bắt" tham số mà service đã truyền cho mock.
    ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(eventRepository).findMistakes(eq(USER_ID), since.capture(), page.capture());

    assertThat(since.getValue()).isEqualTo(Instant.parse("2026-10-27T06:00:00Z"));   // 03/11 − 7 ngày
    assertThat(page.getValue().getPageNumber()).isZero();                            // luôn trang đầu
    assertThat(page.getValue().getPageSize()).isEqualTo(20);
    assertThat(result).containsExactly(improve);                                      // service không đổi gì kết quả
  }

  @Test
  @DisplayName("days = 1: since lùi đúng 24 giờ (02/11 13:00 VN), không phải từ 00:00")
  void list_oneDay_isExactly24Hours() {
    when(eventRepository.findMistakes(anyLong(), any(Instant.class), any(Pageable.class))).thenReturn(List.of());

    mistakeService.list(USER_ID, 1, 20);

    ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
    verify(eventRepository).findMistakes(eq(USER_ID), since.capture(), any(Pageable.class));
    assertThat(since.getValue()).isEqualTo(Instant.parse("2026-11-02T06:00:00Z"));
  }

  @Test
  @DisplayName("Chưa luyện sai lần nào: trả danh sách rỗng, không lỗi")
  void list_noMistakes_returnsEmpty() {
    when(eventRepository.findMistakes(anyLong(), any(Instant.class), any(Pageable.class))).thenReturn(List.of());

    assertThat(mistakeService.list(USER_ID, 7, 20)).isEmpty();
  }

  // ===================== ① Kiểm tra days =====================

  // @ParameterizedTest: CHẠY LẠI cùng một test với nhiều giá trị, mỗi giá trị là một lần chạy riêng.
  @ParameterizedTest(name = "days = {0} → hợp lệ")
  @ValueSource(ints = { 1, 7, 30 })   // hai đầu mút 1 và 30 phải ĐƯỢC chấp nhận
  @DisplayName("days trong [1, 30]: hợp lệ, có gọi repository")
  void list_validDays_callsRepository(int days) {
    when(eventRepository.findMistakes(anyLong(), any(Instant.class), any(Pageable.class))).thenReturn(List.of());

    mistakeService.list(USER_ID, days, 20);

    verify(eventRepository).findMistakes(eq(USER_ID), any(Instant.class), any(Pageable.class));
  }

  @ParameterizedTest(name = "days = {0} → 400 INVALID_DAYS")
  @ValueSource(ints = { 0, -1, 31, 365 })   // ngay ngoài biên (0, 31) và giá trị lạ
  @DisplayName("days ngoài [1, 30]: 400 INVALID_DAYS, KHÔNG chạy query nào")
  void list_invalidDays_isBadRequest(int days) {
    assertThatThrownBy(() -> mistakeService.list(USER_ID, days, 20))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo("INVALID_DAYS");
        });
    // Báo lỗi TRƯỚC khi đụng tới database.
    verifyNoInteractions(eventRepository);
  }

  // ===================== ③ Ép limit =====================

  // @CsvSource: mỗi dòng "đầu vào, kết quả mong đợi".
  @ParameterizedTest(name = "limit = {0} → page size {1}")
  @CsvSource({
      "20, 20",    // bình thường: giữ nguyên
      "1, 1",      // biên dưới
      "50, 50",    // biên trên
      "999, 50",   // quá lớn → ép về 50
      "0, 1",      // 0 → ép về 1 (PageRequest không nhận size 0)
      "-5, 1"      // âm → ép về 1
  })
  @DisplayName("limit bị ép vào [1, 50], không báo lỗi")
  void list_limitIsClamped(int limit, int expectedSize) {
    when(eventRepository.findMistakes(anyLong(), any(Instant.class), any(Pageable.class))).thenReturn(List.of());

    mistakeService.list(USER_ID, 7, limit);

    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(eventRepository).findMistakes(eq(USER_ID), any(Instant.class), page.capture());
    assertThat(page.getValue().getPageSize()).isEqualTo(expectedSize);
  }
}
