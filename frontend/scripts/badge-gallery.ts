/**
 * [THÊM] Sinh trang xem trước hệ thống huy hiệu: docs/badges/badge-gallery.html
 *
 *   npm run docs:badges
 *
 * Dùng CHÍNH hàm buildMedal() + bảng màu + danh mục huy hiệu của app, nên sửa màu / hình / mốc trong code
 * rồi chạy lại lệnh này là tài liệu cập nhật theo, không phải vẽ tay.
 */
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  BADGE_CATEGORY_LABELS, BADGE_CATEGORY_THEMES, BADGE_METALS, BADGE_NEUTRALS, BadgeCategory, Tone,
} from '../src/app/core/models/badge.models';
import { BADGE_CATALOG } from '../src/app/core/mock/mock-badge-catalog';

import { MEDAL_RATIO, MEDAL_VIEWBOX, SPECIAL_RIM, buildMedal } from '../src/app/shared/ui/badge-medal/badge-art';

// Dùng CHÍNH hàm vẽ của app → hình trong tài liệu giống hệt hình trong app.
function medal(code: string, category: BadgeCategory, kind: string, level: number, hidden = false, size = 96): string {
  const parts = buildMedal({ code: code as never, category, kind: kind as never, level, hidden });
  return `<svg viewBox="${MEDAL_VIEWBOX}" width="${size}" height="${(size * MEDAL_RATIO).toFixed(1)}" stroke-linecap="round" stroke-linejoin="round">${parts
    .map((p) => `<path d="${p.d}" fill="${p.fill ?? 'none'}"${p.stroke ? ` stroke="${p.stroke}"` : ''}${p.sw ? ` stroke-width="${p.sw}"` : ''}${p.opacity != null ? ` opacity="${p.opacity}"` : ''}${p.transform ? ` transform="${p.transform}"` : ''}/>`)
    .join('')}</svg>`;
}

const swatch = (name: string, t: Tone, note = '') => `<div class="sw"><div class="chips">${(['light', 'base', 'dark'] as const).map((k) => `<span style="background:${t[k]}" title="${k} ${t[k]}"></span>`).join('')}</div><b>${name}</b><code>${t.light} · ${t.base} · ${t.dark}</code>${note ? `<small>${note}</small>` : ''}</div>`;

const cats = Object.keys(BADGE_CATEGORY_THEMES) as BadgeCategory[];
const tiered = BADGE_CATALOG.filter((b) => b.kind === 'TIERED');
const specials = BADGE_CATALOG.filter((b) => b.kind === 'SPECIAL');

const html = `<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cassin · Hệ thống huy hiệu</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&family=Patrick+Hand&display=swap" rel="stylesheet">
<style>
:root{--ink:#23264a;--soft:#5d6080;--line:#e3ddef;--bg:#fbfaf7}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.55 'Be Vietnam Pro',system-ui,sans-serif}
main{max-width:1120px;margin:0 auto;padding:48px 24px 72px}
h1{font-size:34px;margin:0}h2{font-size:21px;margin:48px 0 6px}p.lead{color:var(--soft);margin:6px 0 0;max-width:760px}
.hand{font-family:'Patrick Hand',cursive}
.card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:20px}
.palette{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:12px;margin-top:16px}
.sw{display:flex;flex-direction:column;gap:2px;background:#fff;border:1px solid var(--line);border-radius:12px;padding:12px}
.chips{display:flex;height:40px;border-radius:8px;overflow:hidden;margin-bottom:6px}.chips span{flex:1}
.sw code{font-size:11.5px;color:var(--soft)}.sw small{color:var(--soft);font-size:12.5px}
table{width:100%;border-collapse:separate;border-spacing:0;margin-top:16px;background:#fff;border:1px solid var(--line);border-radius:14px;overflow:hidden}
th,td{padding:10px 8px;text-align:center;border-bottom:1px solid #f0edf5}th{font-size:13px;color:var(--soft);font-weight:600;background:#faf9fc}
td.n{text-align:left;padding-left:18px;min-width:210px}td.n b{display:block}td.n span{font-size:12.5px;color:var(--soft)}
tr:last-child td{border-bottom:0}.dot{display:inline-block;width:10px;height:10px;border-radius:3px;transform:rotate(45deg);margin-right:8px}
.anat{display:grid;grid-template-columns:auto 1fr;gap:28px;align-items:center}
.anat ol{margin:0;padding-left:20px}.anat li{margin:4px 0}
/* Lưới cột đều, căn ĐỈNH: huy chương luôn thẳng hàng dù chú thích dài ngắn khác nhau */
.row{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:22px;align-items:start;margin-top:16px}.row figure{display:flex;flex-direction:column;align-items:center;margin:0;text-align:center}.row figure svg{flex:none}.row figcaption{margin-top:6px;font-size:13px;color:var(--soft)}
@media(max-width:720px){.anat{grid-template-columns:1fr}}
</style></head><body><main>
<h1>Hệ thống huy hiệu Cassin</h1>
<p class="lead">Một huy hiệu nói hai điều cùng lúc: <b>viền kim loại</b> cho biết cấp đã đạt (màu chung cho mọi nhóm), <b>mặt màu + họa tiết</b> cho biết thành tích về việc gì (màu riêng của nhóm). Mỗi huy hiệu có 3 cấp Đồng → Bạc → Vàng, cấp càng cao càng nhiều chi tiết; huy hiệu đặc biệt dùng khung 12 mặt nền đêm. Hình ở trang này được vẽ bằng chính hàm buildMedal() của app.</p>

<h2>Cấu tạo một huy chương</h2>
<div class="card anat">${medal('DILIGENT', 'REVIEW', 'TIERED', 3, false, 220)}
<ol>
<li><b>Khung cắt mặt</b> kim loại theo cấp: 6 mặt như đá quý, mặt quay về góc trên trái sáng nhất, có bóng khối phía dưới.</li>
<li><b>Lòng lõm</b> màu nhóm: vòng mặt cắt thứ hai chiếu sáng ngược (trên tối, dưới sáng) nên trông sâu xuống.</li>
<li><b>Họa tiết nhóm</b> trắng mờ: sọc, đốm lửa, vòng, chấm, ô cờ, dòng kẻ, răng cưa, tia, sao. Phân biệt nhóm cả khi không nhìn rõ màu.</li>
<li><b>Hình</b> nét trắng dày có bóng, vầng sáng phía sau, 2 điểm lấp lánh và vệt phản chiếu.</li>
<li><b>Tấm đế 3 ô sao</b>: số sao sáng = cấp đã đạt.</li>
<li><b>Cấp II</b> thêm mũi nhọn hai bên + ngọc nhỏ trên đỉnh (màu ngọc = màu nhóm).</li>
<li><b>Cấp III</b> thêm ngọc lớn, đôi cánh và hào quang.</li>
<li><b>Đặc biệt</b>: khung 12 mặt bạc ánh tím, lòng màu đêm, hình và ngọc màu vàng.</li>
</ol></div>

<h2>Lên cấp</h2>
<div class="row">${[0, 1, 2, 3].map((l) => `<figure>${medal('ON_FIRE', 'STREAK', 'TIERED', l, false, 120)}<figcaption>${['Chưa đạt', 'Cấp I · Đồng', 'Cấp II · Bạc', 'Cấp III · Vàng'][l]}</figcaption></figure>`).join('')}
<figure>${medal('COMEBACK', 'SPECIAL', 'SPECIAL', 1, false, 120)}<figcaption>Đặc biệt</figcaption></figure>
<figure>${medal('NIGHT_OWL', 'SPECIAL', 'SPECIAL', 0, true, 120)}<figcaption>Bí mật, chưa đạt</figcaption></figure></div>

<h2>Bảng màu chung</h2>
<p class="lead">Dùng cho mọi nhóm. Kim loại chỉ xuất hiện ở khung, tấm đế, mũi nhọn, cánh và chip cấp; không bao giờ dùng làm màu lòng huy chương.</p>
<div class="palette">${swatch('Đồng · cấp I', BADGE_METALS[1])}${swatch('Bạc · cấp II', BADGE_METALS[2])}${swatch('Vàng · cấp III', BADGE_METALS[3])}${swatch('Viền đặc biệt', SPECIAL_RIM)}${swatch('Chưa đạt', BADGE_NEUTRALS.locked, 'Thay cả khung lẫn mặt: huy hiệu chưa đạt lùi về sau')}</div>

<h2>Bảng màu riêng theo nhóm</h2>
<p class="lead">9 sắc độ trải đều vòng màu để hai nhóm cạnh nhau không lẫn. Mỗi nhóm 3 tông: <i>light</i> (mặt cắt sáng, vầng sáng, nền thẻ), <i>base</i> (lòng huy chương, ngọc, thanh tiến độ), <i>dark</i> (mặt cắt tối, bóng của hình). Các mặt cắt được trộn tự động giữa 3 tông này theo hướng sáng.</p>
<div class="palette">${cats.map((c) => swatch(BADGE_CATEGORY_LABELS[c], BADGE_CATEGORY_THEMES[c], 'Họa tiết: ' + BADGE_CATEGORY_THEMES[c].pattern)).join('')}</div>

<h2>Toàn bộ huy hiệu có cấp</h2>
<table><thead><tr><th style="text-align:left;padding-left:18px">Huy hiệu</th><th>Chưa đạt</th><th>I · Đồng</th><th>II · Bạc</th><th>III · Vàng</th></tr></thead><tbody>
${tiered.map((b) => `<tr><td class="n"><b><span class="dot" style="background:${BADGE_CATEGORY_THEMES[b.category].base}"></span>${b.name}</b><span>${b.description}<br>Mốc: ${b.targets.map((t) => t.toLocaleString('vi-VN')).join(' / ')} ${b.unit}</span></td>${[0, 1, 2, 3].map((l) => `<td>${medal(b.code, b.category, 'TIERED', l, false, 84)}</td>`).join('')}</tr>`).join('')}
</tbody></table>

<h2>Huy hiệu đặc biệt</h2>
<div class="row">${specials.map((b) => `<figure>${medal(b.code, 'SPECIAL', 'SPECIAL', 1, false, 104)}<figcaption><b style="color:var(--ink)">${b.name}${b.secret ? ' (bí mật)' : ''}</b><br>${b.description}</figcaption></figure>`).join('')}</div>
</main></body></html>`;
const out = resolve(__dirname, '../../docs/badges/badge-gallery.html');
writeFileSync(out, html);
console.log('Đã ghi', out);
