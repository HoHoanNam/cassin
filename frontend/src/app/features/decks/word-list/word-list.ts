import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { WordSummary } from '../../../core/models/deck.models';
import { PART_OF_SPEECH } from '../../../core/util/part-of-speech';
import { Icon } from '../../../shared/ui/icon/icon';

/**
 * [V10] Danh sách từ (mỗi từ kèm các nghĩa của nó). Dùng chung cho trang bộ từ của học viên
 * và trang bộ từ hệ thống của admin; trang cha quyết định hiện nút nào.
 *
 *   editAll    : admin sửa được mọi từ hệ thống. Học viên chỉ sửa được từ có editable = true.
 *   removable  : hiện nút "Bỏ khỏi bộ" (chỉ xóa liên kết, từ vẫn còn)
 *   deletable  : hiện nút "Xóa từ" (xóa hẳn, kèm tiến độ ôn)
 *   markSystem : gắn nhãn "Có sẵn" cho từ hệ thống (tắt đi khi cả bộ đều là từ hệ thống)
 */
@Component({
  selector: 'app-word-list',
  imports: [Icon],
  templateUrl: './word-list.html',
  styleUrl: './word-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WordList {
  readonly words = input.required<WordSummary[]>();
  readonly busy = input(false);
  readonly editAll = input(false);
  readonly removable = input(false);
  readonly deletable = input(false);
  readonly markSystem = input(true);
  readonly editWord = output<WordSummary>();
  readonly removeWord = output<WordSummary>();
  readonly deleteWord = output<WordSummary>();

  protected readonly pos = PART_OF_SPEECH;

  protected canEdit(word: WordSummary): boolean {
    return this.editAll() || word.editable;
  }
}
