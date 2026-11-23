package com.cassin.badge;

import java.util.Set;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Luật của MỘT huy hiệu. Mỗi phase thêm vài bean @Component implements BadgeRule, không sửa gì ở base.
 *
 *   code()      mã trong badge_definitions (vd "DILIGENT"). Sai mã thì app không khởi động (BadgeRuleRegistry).
 *   triggers()  những loại sự kiện có thể làm tiến độ đổi. Sự kiện khác → không tính lại, đỡ tốn truy vấn.
 *   progress()  giá trị hiện tại của chỉ số, thường là MỘT câu COUNT trên bảng nghiệp vụ.
 *               So với target từng cấp để biết đạt tới cấp nào. Không lưu, tính lúc cần.
 */
public interface BadgeRule {

  String code();

  Set<Class<? extends BadgeTrigger>> triggers();

  long progress(Long userId);

  /** Rule có quan tâm sự kiện này không (cho phép khai báo cả interface/lớp cha). */
  default boolean listensTo(BadgeTrigger event) {
    return triggers().stream().anyMatch(type -> type.isInstance(event));
  }
}