import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { Icon } from '../../shared/ui/icon/icon';
import { AuthLayout } from './auth-layout/auth-layout';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, Icon],
  templateUrl: './login-page.html',
  styleUrl: './auth-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  /** Lấy từ query string nhờ withComponentInputBinding: /login?returnUrl=/decks&reason=expired */
  readonly returnUrl = input<string>();
  readonly reason = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly submitting = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly sessionExpired = computed(() => this.reason() === 'expired');

  protected submit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      // Hiện lỗi của mọi ô ngay khi bấm nút, kể cả ô chưa chạm vào.
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl(this.safeReturnUrl()),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.serverError.set(
          error instanceof ApiError && error.code === 'INVALID_CREDENTIALS'
            ? 'Email hoặc mật khẩu không đúng. Kiểm tra lại rồi thử lần nữa.'
            : errorMessage(error),
        );
      },
    });
  }

  /** Chỉ cho quay về đường dẫn NỘI BỘ ("/..."), chặn returnUrl=https://trang-la.com (open redirect). */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/today';
  }
}
