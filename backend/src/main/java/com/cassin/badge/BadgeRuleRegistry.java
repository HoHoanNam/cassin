package com.cassin.badge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Gom mọi bean BadgeRule (Spring tự inject List<BadgeRule>). Huy hiệu chưa có rule = chưa "bật":
 * API không trả ra, listener không xét. Nhờ vậy làm từng danh mục một mà không cần cờ bật/tắt trong DB.
 */
@Component
public class BadgeRuleRegistry {
  private final Map<String, BadgeRule> byCode;
  private final BadgeDefinitionRepository definitionRepository;

  /**
   * ObjectProvider thay cho List<BadgeRule>: ở Phase 0 chưa có rule nào. ObjectProvider luôn trả stream (có thể rỗng),
   * không phụ thuộc việc Spring xử lý "List không có bean nào" ra sao.
   */
  public BadgeRuleRegistry(ObjectProvider<BadgeRule> rules, BadgeDefinitionRepository definitionRepository) {
    // toMap ném IllegalStateException nếu hai rule cùng mã → lỗi ngay lúc khởi động, không âm thầm ghi đè.
    this.byCode = rules.orderedStream().collect(Collectors.toUnmodifiableMap(br -> br.code(), Function.identity()));
    this.definitionRepository = definitionRepository;
  }

  public Optional<BadgeRule> find(String code) {
    return Optional.ofNullable(byCode.get(code));
  }

  public boolean isEnabled(String code) {
    return byCode.containsKey(code);
  }

  public Collection<BadgeRule> all() {
    return byCode.values();
  }

  public List<BadgeRule> listeningTo(BadgeTrigger event) {
    return byCode.values().stream().filter(rule -> rule.listensTo(event)).toList();
  }

  /** Gõ sai mã trong rule (vd "DILIGEN") → dừng app khi khởi động thay vì huy hiệu lặng lẽ không bao giờ được cấp. */
  @EventListener(ApplicationReadyEvent.class)
  void verifyCodesExist() {
    Set<String> known = definitionRepository.findAll().stream().map(bd -> bd.getCode())
        .collect(Collectors.toSet());
    List<String> unknown = byCode.keySet().stream().filter(code -> !known.contains(code)).sorted().toList();
    if (!unknown.isEmpty()) {
      throw new IllegalStateException("BadgeRule có mã không có trong badge_definitions: " + unknown);
    }
  }
}