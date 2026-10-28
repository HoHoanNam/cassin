import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ToastService } from '../../../core/ui/toast.service';
import { Icon } from '../icon/icon';

/** Nơi hiện thông báo của ToastService. Đặt một lần ở gốc app. */
@Component({
  selector: 'app-toast-outlet',
  imports: [Icon],
  templateUrl: './toast-outlet.html',
  styleUrl: './toast-outlet.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastOutlet {
  protected readonly toasts = inject(ToastService);
}
