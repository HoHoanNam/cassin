import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, input } from '@angular/core';

import { Mark, highlight, paragraphLabel, paragraphs } from '../../../core/util/passage-text';

/**
 * [THÊM] Hiện bài đọc theo từng đoạn (Paragraph A, B, C...) và tô sáng:
 *   - vàng  (hint)     : cụm đồng nghĩa trong bài mà học viên vừa mở gợi ý
 *   - xanh  (evidence) : câu bằng chứng, ở trang kết quả
 * Dùng chung cho trang làm bài và trang kết quả.
 */
@Component({
  selector: 'app-passage-text',
  templateUrl: './passage-text.html',
  styleUrl: './passage-text.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PassageText {
  readonly content = input.required<string>();
  readonly marks = input<Mark[]>([]);
  /** Có giá trị → cuộn tới chỗ tô sáng đầu tiên (vd. khi bấm "Xem trong bài"). Đổi số để cuộn lại. */
  readonly scrollKey = input<number | null>(null);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly label = paragraphLabel;

  protected readonly blocks = computed(() =>
    paragraphs(this.content()).map((text) => highlight(text, this.marks())),
  );

  constructor() {
    afterRenderEffect(() => {
      if (this.scrollKey() !== null) {
        this.host.nativeElement.querySelector('mark')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    });
  }
}
