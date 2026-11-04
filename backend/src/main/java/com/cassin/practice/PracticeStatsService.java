package com.cassin.practice;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.TimeConfig;
import com.cassin.practice.dto.ModeStat;
import com.cassin.practice.dto.PracticeStatsResponse;
import com.cassin.practice.dto.PracticeStatsResponse.DayStat;

import lombok.RequiredArgsConstructor;

/**
 * Thống kê luyện tập trong N ngày gần nhất, tính theo LỊCH Việt Nam và gồm cả hôm nay.
 * Vd days = 7, hôm nay 03/11 → từ 28/10 đến hết 03/11.
 *
 * Hai query dùng CÙNG một khoảng thời gian (một bản LocalDate, một bản Instant), nên tổng số câu
 * cộng từ byMode luôn bằng tổng số câu cộng từ daily.
 */
@Service
@RequiredArgsConstructor
public class PracticeStatsService {
  static final int MAX_DAYS = 30;

  private final ReviewEventRepository eventRepository;
  private final Clock clock;

  /** GET /api/practice/stats?days= */
  @Transactional(readOnly = true)
  public PracticeStatsResponse stats(Long userId, int days) {
    // 1. Kiểm tra days ∈ [1, MAX_DAYS]. Sai → 400, không âm thầm sửa (giống MistakeService).
    if (days < 1 || days > MAX_DAYS) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAYS", "Số ngày phải từ 1 đến " + MAX_DAYS);
    }

    // 2a. Khoảng NGÀY theo lịch Việt Nam: [fromDate, today], tính CẢ HAI đầu → dùng cho statsByDay.
    //     Clock của Cassin chạy theo APP_ZONE (TimeConfig) → LocalDate.now(clock) là "hôm nay" ở VN.
    //     "7 ngày" = hôm nay + 6 ngày trước → minusDays(days - 1).
    //     minusDays(days) sẽ ra 8 ngày (lỗi lệch một đơn vị: số ngày = cuối − đầu + 1).
    LocalDate today = LocalDate.now(clock);
    LocalDate fromDate = today.minusDays(days - 1);

    // 2b. CÙNG khoảng đó, đổi sang mốc tuyệt đối (Instant) cho statsByMode:
    //     [00:00 ngày đầu, 00:00 ngày SAU hôm nay), giờ Việt Nam. Mốc cuối KHÔNG tính (JPQL dùng "<").
    //     atStartOfDay(APP_ZONE).toInstant() = bản Java của "d.day at time zone 'Asia/Ho_Chi_Minh'".
    //     Vd 28/10 → 27/10 17:00 UTC;  04/11 → 03/11 17:00 UTC.
    Instant from = fromDate.atStartOfDay(TimeConfig.APP_ZONE).toInstant();
    Instant to = today.plusDays(1).atStartOfDay(TimeConfig.APP_ZONE).toInstant();

    // 3. Theo chế độ (JPQL). Chỉ có chế độ ĐÃ luyện: GROUP BY không tự sinh dòng 0 như generate_series.
    List<ModeStat> byMode = eventRepository.statsByMode(userId, from, to);

    // 4. Theo ngày (native, ĐỦ mọi ngày). DailyCount là interface projection, object thật là proxy
    //    của Spring → đổi sang record DayStat để API chỉ trả dữ liệu thuần, không lộ chi tiết repository.
    List<DayStat> daily = eventRepository.statsByDay(userId, fromDate, today).stream()
        .map(d -> new DayStat(d.getDay(), d.getTotal(), d.getCorrect()))
        .toList();

    // 5. Tổng + tỉ lệ đúng (0.0 – 1.0; frontend nhân 100 để hiện %).
    //    (double) đặt TRƯỚC phép chia: long / long bỏ phần thập phân → 38 / 52 = 0.
    //    total == 0 (chưa luyện câu nào) → 0, tránh chia cho 0.
    long total = 0;
    long correct = 0;
    for (ModeStat mode : byMode) {
      total += mode.total();
      correct += mode.correct();
    }
    double accuracy = total == 0 ? 0 : (double) correct / total;

    return new PracticeStatsResponse(total, correct, accuracy, byMode, daily);
  }
}
