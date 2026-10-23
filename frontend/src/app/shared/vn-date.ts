/**
 * Ngày giờ hiển thị theo giờ Việt Nam, bất kể máy người dùng đặt múi giờ nào.
 * (Backend cũng tính "hôm nay" theo Asia/Ho_Chi_Minh, nên hai bên khớp nhau.)
 */
const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';

function vnParts(date: Date): Record<string, string> {
  const parts = new Intl.DateTimeFormat('vi-VN', {
    timeZone: VN_TIME_ZONE,
    weekday: 'long',
    day: 'numeric',
    month: 'numeric',
    year: 'numeric',
  }).formatToParts(date);
  return Object.fromEntries(parts.map((p) => [p.type, p.value]));
}

/** "Thứ Sáu, ngày 23 tháng 10 năm 2026": cách học sinh ghi ngày ở đầu trang vở. */
export function notebookDate(date: Date): string {
  const p = vnParts(date);
  const weekday = p['weekday'].charAt(0).toUpperCase() + p['weekday'].slice(1);
  return `${weekday}, ngày ${p['day']} tháng ${p['month']} năm ${p['year']}`;
}

/** Chuỗi YYYY-MM-DD theo giờ Việt Nam, dùng để so sánh hai ngày. */
function vnDayKey(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: VN_TIME_ZONE }).format(date);
}

/** "Hôm nay", "Hôm qua" hoặc "21/10". */
export function relativeDay(date: Date, now: Date = new Date()): string {
  const day = vnDayKey(date);
  if (day === vnDayKey(now)) {
    return 'Hôm nay';
  }
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  if (day === vnDayKey(yesterday)) {
    return 'Hôm qua';
  }
  const p = vnParts(date);
  return `${p['day'].padStart(2, '0')}/${p['month'].padStart(2, '0')}`;
}
