import { IconName } from '../shared/ui/icon/icon';

/** Các mục điều hướng chính, dùng chung cho sidebar (máy tính) và thanh dưới đáy (điện thoại). */
export interface NavItem {
  label: string;
  icon: IconName;
  link: string;
  /** Hiện số thẻ đến hạn cạnh mục này */
  showDue?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Hôm nay', icon: 'today', link: '/today' },
  { label: 'Ôn thẻ', icon: 'cards', link: '/review', showDue: true },
  { label: 'Bộ từ', icon: 'decks', link: '/decks' },
  { label: 'Ngữ pháp', icon: 'grammar', link: '/grammar' },
  { label: 'Lịch sử', icon: 'history', link: '/quiz-history' },
];

/** Chỉ hiện với tài khoản ADMIN (sidebar). Trên điện thoại vào qua menu tài khoản. */
export const ADMIN_NAV_ITEM: NavItem = { label: 'Quản trị', icon: 'shield', link: '/admin' };
