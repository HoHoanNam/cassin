import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Logo } from '../../shared/ui/logo/logo';
import { ScoreStamp } from '../../shared/ui/score-stamp/score-stamp';

interface FaqItem {
  question: string;
  answer: string;
}

/**
 * Trang giới thiệu cho khách. Nội dung marketing nằm ngay trong file .ts này
 * để sửa chữ không phải đụng tới HTML.
 */
@Component({
  selector: 'app-landing-page',
  imports: [RouterLink, Logo, ScoreStamp],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPage {
  protected readonly year = new Date().getFullYear();

  protected readonly boxes = [
    { box: 1, title: 'Ôn mỗi ngày', note: 'Từ mới và từ vừa quên' },
    { box: 2, title: '2 ngày một lần', note: 'Đã nhớ được một lần' },
    { box: 3, title: '4 ngày một lần', note: 'Bắt đầu quen' },
    { box: 4, title: '7 ngày một lần', note: 'Gần thuộc' },
    { box: 5, title: '15 ngày một lần', note: 'Đã thuộc' },
  ];

  protected readonly faqs: FaqItem[] = [
    {
      question: 'Cassin có mất phí không?',
      // [GIÁ VÀ GÓI CỦA BẠN] Thay câu này khi đã chốt gói dịch vụ.
      answer: 'Bản hiện tại miễn phí trong giai đoạn thử nghiệm. Thông tin gói trả phí sẽ được công bố trước khi áp dụng.',
    },
    {
      question: 'Mỗi ngày nên học bao lâu?',
      answer: 'Chỉ cần ôn hết số thẻ đến hạn trong ngày. Cassin tự tính số thẻ đó, nên có hôm nhiều, có hôm ít.',
    },
    {
      question: 'Bỏ lỡ một ngày thì sao?',
      answer: 'Thẻ không mất đi. Hôm sau các thẻ quá hạn sẽ được đưa lên trước để bạn ôn bù.',
    },
    {
      question: 'Tôi có tự thêm từ được không?',
      answer: 'Được. Tạo một bộ từ riêng, mỗi thẻ gồm từ, loại từ, nghĩa và câu ví dụ.',
    },
  ];
}
