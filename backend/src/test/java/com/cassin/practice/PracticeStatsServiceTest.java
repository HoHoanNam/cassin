package com.cassin.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.common.TimeConfig;
import com.cassin.practice.dto.DailyCount;
import com.cassin.practice.dto.ModeStat;
import com.cassin.practice.dto.PracticeStatsResponse;
import com.cassin.practice.dto.PracticeStatsResponse.DayStat;

/**
 * Thống kê luyện tập: PracticeStatsService làm 3 việc, mỗi việc một nhóm test.
 *   ① kiểm tra days (1–30)
 *   ② tính khoảng thời gian theo LỊCH Việt Nam (LocalDate cho statsByDay, Instant cho statsByMode)
 *   ③ đổi DailyCount → DayStat, cộng tổng, tính accuracy
 *
 * Repository là MOCK: câu SQL không chạy ở đây (đã kiểm tra bằng pgAdmin + gọi API thật).
 */
@ExtendWith(MockitoExtension.class)
class PracticeStatsServiceTest {
  private static final Long USER_ID = 1L;

  @Mock ReviewEventRepository eventRepository;

  /**
   * DailyCount là INTERFACE → test cần một class cài đặt nó để làm dữ liệu giả.
   * Mẹo: record có component tên getDay sẽ tự sinh method getDay() → khớp đúng interface.
   */
  private record Day(LocalDate getDay, Long getTotal, Long getCorrect) implements DailyCount {
  }

  /** Tạo service với "bây giờ" cố định. Clock chạy theo giờ Việt Nam, giống TimeConfig của app. */
  private PracticeStatsService serviceAt(String nowUtc) {
    Clock clock = Clock.fixed(Instant.parse(nowUtc), TimeConfig.APP_ZONE);
    return new PracticeStatsService(eventRepository, clock);
  }

  // 04/11/2026 12:00 giờ Việt Nam = 05:00 UTC
  private static final String NOON_04_11 = "2026-11-04T05:00:00Z";

  // ===================== ② Khoảng thời gian =====================

  @Test
  @DisplayName("7 ngày: statsByDay nhận 29/10 → 04/11; statsByMode nhận [29/10 00:00 VN, 05/11 00:00 VN)")
  void sevenDays_rangeIsCalendarDaysInVietnam() {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    serviceAt(NOON_04_11).stats(USER_ID, 7);

    // 7 ngày = hôm nay + 6 ngày trước (minusDays(days - 1)), tính CẢ HAI đầu
    verify(eventRepository).statsByDay(USER_ID, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 4));
    // Cùng khoảng đó nhưng là mốc tuyệt đối: 00:00 VN = 17:00 UTC hôm trước. Mốc cuối = 00:00 NGÀY SAU hôm nay.
    verify(eventRepository).statsByMode(USER_ID,
        Instant.parse("2026-10-28T17:00:00Z"),
        Instant.parse("2026-11-04T17:00:00Z"));
  }

  @Test
  @DisplayName("days = 1: chỉ hôm nay, từ 00:00 đến hết ngày (giờ VN)")
  void oneDay_isTodayOnly() {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    serviceAt(NOON_04_11).stats(USER_ID, 1);

    verify(eventRepository).statsByDay(USER_ID, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));
    verify(eventRepository).statsByMode(USER_ID,
        Instant.parse("2026-11-03T17:00:00Z"),
        Instant.parse("2026-11-04T17:00:00Z"));
  }

  @Test
  @DisplayName("00:30 sáng 04/11 giờ VN (= 03/11 theo UTC): 'hôm nay' vẫn là 04/11")
  void justAfterMidnight_todayFollowsVietnamCalendar() {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    // 03/11 17:30 UTC = 04/11 00:30 giờ Việt Nam
    serviceAt("2026-11-03T17:30:00Z").stats(USER_ID, 7);

    // Nếu Clock chạy theo UTC, toDate sẽ thành 03/11 → biểu đồ thiếu mất "hôm nay"
    verify(eventRepository).statsByDay(eq(USER_ID), eq(LocalDate.of(2026, 10, 29)), eq(LocalDate.of(2026, 11, 4)));
  }

  // ===================== ③ Ghép kết quả =====================

  @Test
  @DisplayName("Cộng tổng từ byMode, accuracy = đúng / tổng, daily đổi sang DayStat giữ nguyên thứ tự")
  void combinesModesAndDays() {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of(
        new ModeStat(PracticeMode.FILL_BLANK, 3L, 2L),
        new ModeStat(PracticeMode.SENTENCE_BUILD, 1L, 1L)));
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
        new Day(LocalDate.of(2026, 11, 3), 0L, 0L),
        new Day(LocalDate.of(2026, 11, 4), 4L, 3L)));

    PracticeStatsResponse stats = serviceAt(NOON_04_11).stats(USER_ID, 2);

    assertThat(stats.total()).isEqualTo(4);
    assertThat(stats.correct()).isEqualTo(3);
    assertThat(stats.accuracy()).isEqualTo(0.75);
    assertThat(stats.byMode()).extracting(ModeStat::mode)
        .containsExactly(PracticeMode.FILL_BLANK, PracticeMode.SENTENCE_BUILD);
    assertThat(stats.daily()).containsExactly(
        new DayStat(LocalDate.of(2026, 11, 3), 0, 0),
        new DayStat(LocalDate.of(2026, 11, 4), 4, 3));
  }

  @Test
  @DisplayName("accuracy không bị chia nguyên: 38 / 52 ≈ 0.73, không phải 0")
  void accuracy_isNotIntegerDivision() {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(new ModeStat(PracticeMode.FILL_BLANK, 52L, 38L)));
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    PracticeStatsResponse stats = serviceAt(NOON_04_11).stats(USER_ID, 7);

    // within: so số thực phải cho phép sai số nhỏ (0.1 + 0.2 != 0.3 trong máy tính)
    assertThat(stats.accuracy()).isCloseTo(38.0 / 52, within(1e-9));
  }

  @Test
  @DisplayName("Chưa luyện câu nào: total = 0, accuracy = 0 (không chia cho 0, không ra NaN)")
  void noPractice_accuracyIsZero() {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
        new Day(LocalDate.of(2026, 11, 4), 0L, 0L)));

    PracticeStatsResponse stats = serviceAt(NOON_04_11).stats(USER_ID, 1);

    assertThat(stats.total()).isZero();
    assertThat(stats.accuracy()).isZero();   // 0.0 / 0 trong Java ra NaN → phải chặn
    assertThat(stats.byMode()).isEmpty();
    assertThat(stats.daily()).containsExactly(new DayStat(LocalDate.of(2026, 11, 4), 0, 0));
  }

  // ===================== ① Kiểm tra days =====================

  @ParameterizedTest(name = "days = {0} → hợp lệ")
  @ValueSource(ints = { 1, 30 })   // hai đầu mút phải được chấp nhận
  @DisplayName("days ở biên [1, 30]: hợp lệ")
  void validDays_atBoundaries(int days) {
    when(eventRepository.statsByMode(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    when(eventRepository.statsByDay(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    serviceAt(NOON_04_11).stats(USER_ID, days);

    verify(eventRepository).statsByMode(eq(USER_ID), any(Instant.class), any(Instant.class));
  }

  @ParameterizedTest(name = "days = {0} → 400 INVALID_DAYS")
  @ValueSource(ints = { 0, -1, 31 })
  @DisplayName("days ngoài [1, 30]: 400 INVALID_DAYS, KHÔNG chạy query nào")
  void invalidDays_isBadRequest(int days) {
    PracticeStatsService service = serviceAt(NOON_04_11);

    assertThatThrownBy(() -> service.stats(USER_ID, days))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo("INVALID_DAYS");
        });
    verifyNoInteractions(eventRepository);
  }
}
