import { IconName } from '../shared/ui/icon/icon';

/** Các mục điều hướng chính, dùng chung cho sidebar (máy tính) và thanh dưới đáy (điện thoại). */
export interface NavItem {
  label: string;
  icon: IconName;
  link: string;
  /** Hiện số thẻ đến hạn cạnh mục này */
  showDue?: boolean;
  /** Chỉ hiện ở sidebar. Thanh dưới đáy điện thoại chỉ đủ chỗ cho 5 mục. */
  desktopOnly?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Hôm nay', icon: 'today', link: '/today' },
  { label: 'Ôn thẻ', icon: 'cards', link: '/review', showDue: true },
  { label: 'Bộ từ', icon: 'decks', link: '/decks' },
  { label: 'Thư viện', icon: 'library', link: '/library', desktopOnly: true },
  { label: 'Quét đoạn văn', icon: 'scan', link: '/scan', desktopOnly: true },
  // [THÊM] Luyện tập thay chỗ Lịch sử trên thanh dưới đáy (Lịch sử vẫn vào từ trang Hôm nay / Ngữ pháp)
  { label: 'Luyện tập', icon: 'target', link: '/practice' },
  { label: 'Ngữ pháp', icon: 'grammar', link: '/grammar' },
  { label: 'Lịch sử', icon: 'history', link: '/quiz-history', desktopOnly: true },
];

/** Chỉ hiện với tài khoản ADMIN (sidebar). Trên điện thoại vào qua menu tài khoản. */
export const ADMIN_NAV_ITEM: NavItem = { label: 'Quản trị', icon: 'shield', link: '/admin' };
