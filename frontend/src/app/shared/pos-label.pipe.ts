import { Pipe, PipeTransform } from '@angular/core';

import { POS_LABELS, PartOfSpeech } from './part-of-speech';

/**
 * Hiển thị loại từ trong template: {{ card.partOfSpeech | posLabel }} → "n."
 * {{ card.partOfSpeech | posLabel: 'full' }} → "danh từ".
 *
 * Pipe là "pure" (mặc định): Angular chỉ tính lại khi giá trị đầu vào đổi,
 * nên dùng trong template thoải mái, khác với gọi hàm trực tiếp.
 */
@Pipe({ name: 'posLabel' })
export class PosLabelPipe implements PipeTransform {
  transform(value: PartOfSpeech, mode: 'short' | 'full' = 'short'): string {
    return POS_LABELS[value][mode];
  }
}
