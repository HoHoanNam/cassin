import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { AccountApi } from '../../core/api/account.api';
import { AuthApi } from '../../core/api/auth.api';
import { AuthStore } from '../../core/auth/auth.store';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { PROFILE_LIMITS } from '../../core/models/user.models';
import { ToastService } from '../../core/ui/toast.service';
import { blankToNull, initial } from '../../core/util/text';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/** Ô "Nhập lại mật khẩu mới" phải trùng ô "Mật khẩu mới". Gắn lên cả group vì cần đọc 2 ô. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const next = group.get('newPassword')?.value as string;
  const confirm = group.get('confirmPassword')?.value as string;
  return !confirm || next === confirm ? null : { mismatch: true };
}

/**
 * Trang tài khoản: sửa hồ sơ (tên hiển thị, giới thiệu) và đổi mật khẩu.
 * Hai form độc lập, mỗi form tự có nút lưu, trạng thái đang lưu và lỗi riêng.
 */
@Component({
  selector: 'app-account-page',
  imports: [ReactiveFormsModule, ErrorState, Skeleton],
  templateUrl: './account-page.html',
  styleUrl: './account-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountPage {
  private readonly authApi = inject(AuthApi);
  private readonly accountApi = inject(AccountApi);
  private readonly store = inject(AuthStore);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly limits = PROFILE_LIMITS;

  /** Lấy bản mới nhất từ GET /api/me (phiên đăng nhập có thể giữ thông tin cũ, ví dụ chưa có bio). */
  protected readonly me = rxResource({ stream: () => this.authApi.me() });
  protected readonly avatar = computed(() => initial(this.me.value()?.displayName));

  // ---------- Hồ sơ ----------
  protected readonly profileForm = this.fb.group({
    displayName: ['', [Validators.required, Validators.maxLength(PROFILE_LIMITS.displayName)]],
    bio: ['', Validators.maxLength(PROFILE_LIMITS.bio)],
  });
  protected readonly savingProfile = signal(false);
  protected readonly profileError = signal<string | null>(null);

  // ---------- Mật khẩu ----------
  protected readonly passwordForm = this.fb.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: [
        '',
        [
          Validators.required,
          Validators.minLength(PROFILE_LIMITS.passwordMin),
          Validators.maxLength(PROFILE_LIMITS.passwordMax),
        ],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );
  protected readonly savingPassword = signal(false);
  protected readonly passwordError = signal<string | null>(null);

  constructor() {
    // Có dữ liệu từ /api/me → đổ vào form hồ sơ.
    effect(() => {
      const user = this.me.value();
      if (user) {
        this.profileForm.reset({ displayName: user.displayName, bio: user.bio ?? '' });
      }
    });
  }

  protected saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const v = this.profileForm.getRawValue();
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.accountApi.updateProfile({ displayName: v.displayName.trim(), bio: blankToNull(v.bio) }).subscribe({
      next: (user) => {
        this.savingProfile.set(false);
        // Cập nhật phiên: tên mới hiện ngay ở sidebar / menu tài khoản, không cần đăng nhập lại.
        this.store.updateUser(user);
        this.me.set(user);
        this.toast.success('Đã lưu hồ sơ');
      },
      error: (error: unknown) => {
        this.savingProfile.set(false);
        if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          for (const [field, message] of Object.entries(error.fieldErrors)) {
            this.profileForm.get(field)?.setErrors({ server: message });
          }
        } else {
          this.profileError.set(errorMessage(error));
        }
      },
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const v = this.passwordForm.getRawValue();
    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.accountApi.changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordForm.reset();
        this.toast.success('Đã đổi mật khẩu. Lần đăng nhập sau hãy dùng mật khẩu mới.');
      },
      error: (error: unknown) => {
        this.savingPassword.set(false);
        if (error instanceof ApiError && error.code === 'WRONG_PASSWORD') {
          this.passwordForm.controls.currentPassword.setErrors({ server: error.message });
        } else if (error instanceof ApiError && (error.code === 'SAME_PASSWORD' || error.code === 'PASSWORD_TOO_LONG')) {
          // PASSWORD_TOO_LONG: BCrypt giới hạn 72 BYTE, chữ có dấu tốn 2–3 byte (xem PasswordRules ở backend).
          this.passwordForm.controls.newPassword.setErrors({ server: error.message });
        } else if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          for (const [field, message] of Object.entries(error.fieldErrors)) {
            this.passwordForm.get(field)?.setErrors({ server: message });
          }
        } else {
          this.passwordError.set(errorMessage(error));
        }
      },
    });
  }
}
