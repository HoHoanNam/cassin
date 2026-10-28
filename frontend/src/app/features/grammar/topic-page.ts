import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { GrammarApi } from '../../core/api/grammar.api';
import { ApiError } from '../../core/models/common.models';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/** Một khối trong bài lý thuyết: đoạn văn hoặc danh sách gạch đầu dòng. */
type Block = { kind: 'p'; text: string } | { kind: 'ul'; items: string[] };

/** Trang lý thuyết của một chủ đề + nút bắt đầu làm bài. */
@Component({
  selector: 'app-topic-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon, LevelBadge, Skeleton],
  templateUrl: './topic-page.html',
  styleUrl: './topic-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopicPage {
  readonly topicId = input.required({ transform: numberAttribute });

  private readonly api = inject(GrammarApi);

  protected readonly topic = rxResource({
    params: () => this.topicId(),
    stream: ({ params }) => this.api.getTopic(params),
  });

  protected readonly notFound = computed(() => {
    const error = this.topic.error();
    return error instanceof ApiError && error.status === 404;
  });

  /**
   * Bài giải thích lưu dạng chữ thường (không HTML) để không bị chèn mã độc.
   * Quy ước: các đoạn cách nhau MỘT dòng trống; dòng bắt đầu bằng "• " là gạch đầu dòng.
   * Hiển thị bằng {{ }} của Angular nên mọi ký tự đặc biệt đều được hiện đúng, không thành thẻ HTML.
   */
  protected readonly blocks = computed<Block[]>(() => {
    if (!this.topic.hasValue()) {
      return [];
    }
    const blocks: Block[] = [];
    for (const paragraph of this.topic.value().explanation.split(/\n\s*\n/)) {
      const lines = paragraph.split('\n').map((l) => l.trim()).filter(Boolean);
      const text = lines.filter((l) => !l.startsWith('•'));
      const bullets = lines.filter((l) => l.startsWith('•')).map((l) => l.replace(/^•\s*/, ''));
      if (text.length) {
        blocks.push({ kind: 'p', text: text.join(' ') });
      }
      if (bullets.length) {
        blocks.push({ kind: 'ul', items: bullets });
      }
    }
    return blocks;
  });
}
