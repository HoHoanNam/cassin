import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-header.html',
  styleUrl: './app-header.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppHeader {
  // Danh sách bộ từ và chủ đề ngữ pháp đang nằm ở trang chủ, nên menu chỉ có 2 mục.
  protected readonly links = [
    { path: '/', label: 'Trang chủ', exact: true },
    { path: '/review', label: 'Ôn thẻ', exact: false },
  ];
}
