import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../../core/api/admin.api';
import { AdminStats } from '../../core/models/admin.models';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon, IconName } from '../../shared/ui/icon/icon';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

interface StatTile {
  key: keyof AdminStats;
  label: string;
  icon: IconName;
}

/** Trang đầu của khu vực quản trị: số liệu tổng + lối tắt tới từng phần. */
@Component({
  selector: 'app-admin-overview-page',
  imports: [RouterLink, ErrorState, Icon, Skeleton],
  templateUrl: './admin-overview-page.html',
  styleUrl: './admin-overview-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminOverviewPage {
  private readonly api = inject(AdminApi);

  protected readonly stats = rxResource({ stream: () => this.api.stats() });

  /** Nhóm 1: người học. Nhóm 2: nội dung. Mỗi nhóm một hàng để mắt đọc theo cụm. */
  protected readonly learnerTiles: StatTile[] = [
    { key: 'users', label: 'Tài khoản', icon: 'users' },
    { key: 'learnersToday', label: 'Có học hôm nay', icon: 'flame' },
    { key: 'quizAttempts', label: 'Lượt làm bài', icon: 'history' },
  ];

  protected readonly contentTiles: StatTile[] = [
    { key: 'systemDecks', label: 'Bộ từ hệ thống', icon: 'decks' },
    { key: 'systemCards', label: 'Thẻ hệ thống', icon: 'cards' },
    { key: 'topics', label: 'Chủ đề ngữ pháp', icon: 'grammar' },
    { key: 'questions', label: 'Câu hỏi', icon: 'check' },
  ];
}
