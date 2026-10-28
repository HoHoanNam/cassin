import { CanDeactivateFn } from '@angular/router';
import { Observable } from 'rxjs';

/** Trang nào muốn hỏi lại trước khi rời đi thì cài method này. */
export interface ConfirmLeave {
  confirmLeave(): boolean | Observable<boolean>;
}

/**
 * Đang làm bài dở mà bấm Thoát / nút Back của trình duyệt → hỏi lại,
 * tránh mất hết đáp án đã chọn chỉ vì lỡ tay.
 */
export const quizLeaveGuard: CanDeactivateFn<ConfirmLeave> = (component) => component.confirmLeave();
