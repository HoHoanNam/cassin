package com.cassin.badge;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.badge.dto.BadgeResponse;
import com.cassin.badge.dto.EarnedBadgeResponse;
import com.cassin.badge.dto.SeenBadgeItem;
import com.cassin.badge.dto.TierResponse;

import lombok.RequiredArgsConstructor;

/**
 * Đọc huy hiệu cho người học + đánh dấu đã xem. KHÔNG cấp huy hiệu (đó là việc của BadgeAwardService).
 *
 * Luật:
 *   1. chỉ trả huy hiệu đã có BadgeRule (danh mục chưa làm thì chưa hiện)
 *   2. bí mật chưa đạt: che tên / mô tả / tiến độ
 *   3. unseen: mỗi huy hiệu chỉ trả CẤP CAO NHẤT chưa xem (nhảy I → III chỉ hiện popup III)
 *   4. seen cấp N = đã xem mọi cấp <= N; mã / cấp lạ bị bỏ qua (gọi lại bao nhiêu lần cũng được)
 */
@Service
@RequiredArgsConstructor
public class BadgeService {
  static final String SECRET_NAME = "???";
  static final String SECRET_DESCRIPTION = "Huy hiệu bí mật. Cứ học đi, rồi sẽ biết.";

  private final BadgeRuleRegistry registry;
  private final BadgeDefinitionRepository definitionRepository;
  private final BadgeTierRepository tierRepository;
  private final UserBadgeRepository userBadgeRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<BadgeResponse> list(Long userId) {
    Map<String, List<BadgeTier>> tiers = tiersByCode();
    Map<String, List<UserBadge>> owned = userBadgeRepository.findByIdUserId(userId).stream()
        .collect(Collectors.groupingBy(UserBadge::badgeCode));

    return definitionRepository.findAllByOrderBySortOrderAsc().stream()
        .filter(def -> registry.isEnabled(def.getCode()))
        .map(def -> toResponse(def, tiers.getOrDefault(def.getCode(), List.of()),
            owned.getOrDefault(def.getCode(), List.of()), userId))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EarnedBadgeResponse> unseen(Long userId) {
    List<UserBadge> mine = userBadgeRepository.findByIdUserId(userId);
    Map<String, BadgeDefinition> definitions = definitionRepository.findAll().stream()
        .collect(Collectors.toMap(BadgeDefinition::getCode, Function.identity()));
    Map<String, List<BadgeTier>> tiers = tiersByCode();

    return mine.stream()
        .filter(ub -> ub.getSeenAt() == null)
        // Có cấp cao hơn (đã xem hay chưa) thì cấp này không cần popup riêng.
        .filter(ub -> mine.stream().noneMatch(o -> o.badgeCode().equals(ub.badgeCode()) && o.level() > ub.level()))
        .sorted(Comparator.comparing(UserBadge::getEarnedAt).thenComparing(UserBadge::badgeCode))
        .map(ub -> toEarned(definitions.get(ub.badgeCode()), tiers.get(ub.badgeCode()), ub))
        .toList();
  }

  @Transactional
  public void markSeen(Long userId, List<SeenBadgeItem> items) {
    Instant now = clock.instant();
    for (SeenBadgeItem item : items) {
      userBadgeRepository.markSeen(userId, item.code(), (short) item.level(), now);
    }
  }

  // ---------------------------------------------------------------- dựng DTO

  private BadgeResponse toResponse(BadgeDefinition def, List<BadgeTier> tiers, List<UserBadge> owned, Long userId) {
    Map<Integer, Instant> earnedAt = owned.stream()
        .collect(Collectors.toMap(UserBadge::level, UserBadge::getEarnedAt));
    int level = owned.stream().mapToInt(UserBadge::level).max().orElse(0);

    if (def.isSecret() && level == 0) {
      List<TierResponse> hidden = tiers.stream().map(t -> new TierResponse(t.level(), 1, null)).toList();
      return new BadgeResponse(def.getCode(), def.getKind(), SECRET_NAME, SECRET_DESCRIPTION, "",
          def.getCategory(), true, 0, 0, hidden);
    }

    long progress = registry.find(def.getCode()).map(rule -> rule.progress(userId)).orElse(0L);
    List<TierResponse> tierResponses = tiers.stream()
        .map(t -> new TierResponse(t.level(), t.getTarget(), earnedAt.get(t.level())))
        .toList();
    return new BadgeResponse(def.getCode(), def.getKind(), def.getName(), def.getDescription(), def.getUnit(),
        def.getCategory(), def.isSecret(), level, progress, tierResponses);
  }

  private static EarnedBadgeResponse toEarned(BadgeDefinition def, List<BadgeTier> tiers, UserBadge ub) {
    int target = tiers.stream().filter(t -> t.level() == ub.level()).mapToInt(BadgeTier::getTarget).findFirst()
        .orElse(0);
    return new EarnedBadgeResponse(def.getCode(), def.getKind(), def.getName(), def.getDescription(), def.getUnit(),
        def.getCategory(), ub.level(), target, tiers.size(), ub.getEarnedAt());
  }

  private Map<String, List<BadgeTier>> tiersByCode() {
    return tierRepository.findAllOrdered().stream().collect(Collectors.groupingBy(BadgeTier::badgeCode));
  }
}
