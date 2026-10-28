import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { ToastService } from '../../core/ui/toast.service';
import { Icon } from '../../shared/ui/icon/icon';
import { AuthLayout } from './auth-layout/auth-layout';

/** Giới hạn khớp với RegisterRequest ở backend. */
const PASSWORD_MIN = 8;

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, Icon],
  templateUrl: './register-page.html',
  styleUrl: './auth-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly passwordMin = PASSWORD_MIN;
  protected readonly form = inject(NonNullableFormBuilder).group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN)]],
  });

  protected readonly submitting = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly emailTaken = signal(false);

  protected submit(): void {
    this.serverError.set(null);
    this.emailTaken.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const value = this.form.getRawValue();
    this.auth.register({ ...value, displayName: value.displayName.trim() }).subscribe({
      next: (user) => {
        this.toast.success(`Chào ${user.displayName}, tài khoản đã sẵn sàng.`);
        void this.router.navigateByUrl('/today');
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (error instanceof ApiError && error.code === 'EMAIL_TAKEN') {
          this.emailTaken.set(true);
          return;
        }
        // Lỗi validation từ backend: gắn vào đúng ô
        if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          for (const [field, message] of Object.entries(error.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: message });
          }
          return;
        }
        this.serverError.set(errorMessage(error));
      },
    });
  }
}
