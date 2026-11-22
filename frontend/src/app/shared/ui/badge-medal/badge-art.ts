import {
  BADGE_CATEGORY_THEMES,
  BADGE_METALS,
  BADGE_NEUTRALS,
  BadgeCategory,
  BadgeCode,
  BadgeKind,
  Tone,
} from '../../../core/models/badge.models';
import { BADGE_GLYPHS, SECRET_GLYPH } from './badge-glyphs';
import { CX, CY, circle, pattern, roundedPath, star } from './badge-shapes';

/*
 * [THÊM] "Bản vẽ" huy chương: một hàm thuần buildMedal() trả về danh sách lớp (path + màu).
 * Component Angular chỉ việc lặp và vẽ; trang xem trước bảng màu cũng gọi đúng hàm này,
 * nên hình trong app và hình trong tài liệu luôn giống hệt nhau.
 *
 * Phong cách lấy cảm hứng từ rank badge trong game:
 *   - Khung lục giác CẮT MẶT như đá quý: 6 mặt, mặt quay về góc trên trái sáng nhất.
 *   - Lòng huy chương LÕM xuống: vòng mặt cắt thứ hai chiếu sáng ngược (trên tối, dưới sáng).
 *   - TẤM ĐẾ phía dưới mang 3 ô sao = cấp.
 *   - Lên cấp thì thêm chi tiết: II mũi nhọn hai bên + ngọc nhỏ trên đỉnh,
 *     III ngọc lớn + đôi cánh + hào quang. Người học nhìn hình dáng là biết cấp, không cần đọc chữ.
 */

export interface MedalPart {
  d: string;
  fill?: string;
  stroke?: string;
  sw?: number;
  opacity?: number;
  transform?: string;
}

export interface MedalOptions {
  code: BadgeCode;
  category: BadgeCategory;
  kind: BadgeKind;
  /** 0 = chưa đạt */
  level: number;
  /** Bí mật và chưa đạt: dấu hỏi */
  hidden?: boolean;
  /** Hiện tấm đế + sao */
  base?: boolean;
}

/** Khung nhìn chung cho mọi huy chương (chừa chỗ cho cánh và hào quang). */
export const MEDAL_VIEWBOX = '-16 -6 152 146';
export const MEDAL_RATIO = 146 / 152;

export const SPECIAL_RIM: Tone = { light: '#F4F3FF', base: '#C3C4F2', dark: '#5E61B8' };
const SPECIAL_GLYPH = '#FFD34D';
const LOCKED_FACE: Tone = { light: '#FFFFFF', base: '#ECE9F3', dark: '#CFC9DD' };

// ---------------------------------------------------------------- màu

const hex = (n: number) =>
  Math.round(Math.max(0, Math.min(255, n)))
    .toString(16)
    .padStart(2, '0');
const rgb = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));

/** Trộn hai màu hex: t = 0 → a, t = 1 → b. */
export function mix(a: string, b: string, t: number): string {
  const [r1, g1, b1] = rgb(a);
  const [r2, g2, b2] = rgb(b);
  return `#${hex(r1 + (r2 - r1) * t)}${hex(g1 + (g2 - g1) * t)}${hex(b1 + (b2 - b1) * t)}`;
}

/** Hướng ánh sáng: từ góc trên trái. */
const LIGHT = { x: -0.55, y: -0.83 };

/** Màu một mặt cắt theo pháp tuyến: hướng về nguồn sáng → light, quay đi → dark. */
function shade(tone: Tone, nx: number, ny: number, invert = false): string {
  let k = nx * LIGHT.x + ny * LIGHT.y;
  if (invert) k = -k;
  return k >= 0 ? mix(tone.base, tone.light, k * 0.85) : mix(tone.base, tone.dark, -k * 0.75);
}

// ---------------------------------------------------------------- hình

type Pt = [number, number];
const f = (n: number) => n.toFixed(2);
const poly = (pts: Pt[]) => 'M' + pts.map(([x, y]) => `${f(x)} ${f(y)}`).join('L') + 'Z';
const mirror = (pts: Pt[]): Pt[] => pts.map(([x, y]) => [2 * CX - x, y]);

/** Đỉnh đa giác đều n cạnh, đỉnh đầu tiên hướng lên. */
function vertices(n: number, r: number, cy = CY): Pt[] {
  return Array.from({ length: n }, (_, i) => {
    const a = ((2 * Math.PI) / n) * i - Math.PI / 2;
    return [CX + r * Math.cos(a), cy + r * Math.sin(a)];
  });
}

/** Vòng mặt cắt giữa hai đa giác đồng tâm: mỗi cạnh một hình thang, tô theo hướng sáng. */
function facetRing(
  n: number,
  rOuter: number,
  rInner: number,
  tone: Tone,
  invert = false,
): MedalPart[] {
  const o = vertices(n, rOuter);
  const i = vertices(n, rInner);
  return o.map((_, k) => {
    const k2 = (k + 1) % n;
    const mx = (o[k][0] + o[k2][0]) / 2 - CX;
    const my = (o[k][1] + o[k2][1]) / 2 - CY;
    const len = Math.hypot(mx, my);
    return { d: poly([o[k], o[k2], i[k2], i[k]]), fill: shade(tone, mx / len, my / len, invert) };
  });
}

/** Lấp lánh 4 cánh. */
function sparkle(cx: number, cy: number, r: number): string {
  const k = r * 0.22;
  return `M${f(cx)} ${f(cy - r)}Q${f(cx + k)} ${f(cy - k)} ${f(cx + r)} ${f(cy)}Q${f(cx + k)} ${f(cy + k)} ${f(cx)} ${f(cy + r)}Q${f(cx - k)} ${f(cy + k)} ${f(cx - r)} ${f(cy)}Q${f(cx - k)} ${f(cy - k)} ${f(cx)} ${f(cy - r)}Z`;
}

/** Viên ngọc hình thoi trên đỉnh (2 nửa sáng / tối + vạch sáng giữa). */
function gem(cy: number, w: number, h: number, tone: Tone, rim: Tone): MedalPart[] {
  const top: Pt = [CX, cy - h / 2];
  const bottom: Pt = [CX, cy + h / 2];
  const left: Pt = [CX - w / 2, cy];
  const right: Pt = [CX + w / 2, cy];
  const pad = 3;
  return [
    {
      d: poly([
        [CX, top[1] - pad],
        [right[0] + pad, cy],
        [CX, bottom[1] + pad],
        [left[0] - pad, cy],
      ]),
      fill: rim.dark,
    },
    { d: poly([top, right, bottom, left]), fill: tone.base },
    { d: poly([top, [CX, cy], left]), fill: tone.light },
    { d: poly([[CX, cy], right, bottom]), fill: tone.dark },
    {
      d: poly([
        [CX, top[1] + h * 0.18],
        [CX + w * 0.12, cy],
        [CX, bottom[1] - h * 0.18],
        [CX - w * 0.12, cy],
      ]),
      fill: '#ffffff',
      opacity: 0.7,
    },
  ];
}

/** Mũi nhọn hai bên (cấp II trở lên): mỗi mũi 2 nửa sáng / tối. */
function sideSpikes(tone: Tone, x0: number): MedalPart[] {
  const tip = x0 - 17;
  const left: [Pt[], Pt[]] = [
    [
      [x0, CY - 12],
      [tip, CY],
      [x0, CY],
    ],
    [
      [x0, CY],
      [tip, CY],
      [x0, CY + 12],
    ],
  ];
  const parts: MedalPart[] = [];
  for (const [upper, lower] of [left, [mirror(left[0]), mirror(left[1])] as [Pt[], Pt[]]]) {
    parts.push({ d: poly(upper), fill: tone.light }, { d: poly(lower), fill: tone.dark });
  }
  return parts;
}

/** Đôi cánh (cấp III): 3 lông cong mỗi bên, dài nhất ở trên, có gân sáng. */
function wings(tone: Tone): MedalPart[] {
  const feather = (y0: number, tipX: number, tipY: number) =>
    `M32 ${y0}C14 ${y0 - 1} ${tipX + 8} ${tipY + 4} ${tipX} ${tipY}C${tipX + 6} ${tipY + 12} 14 ${y0 + 13} 32 ${y0 + 14}Z`;
  const vein = (y0: number, tipX: number, tipY: number) =>
    `M30 ${y0 + 7}Q14 ${y0 + 4} ${tipX + 6} ${tipY + 4}`;
  const rows: [number, number, number, string][] = [
    [64, -4, 68, tone.base],
    [50, -12, 44, mix(tone.light, tone.base, 0.45)],
    [36, -18, 18, tone.light],
  ];
  const parts: MedalPart[] = [];
  for (const [y0, tx, ty, fill] of rows) {
    for (const flip of [false, true]) {
      const t = flip ? `matrix(-1 0 0 1 ${2 * CX} 0)` : undefined;
      parts.push({ d: feather(y0, tx, ty), fill, stroke: tone.dark, sw: 1.3, transform: t });
      parts.push({ d: vein(y0, tx, ty), stroke: '#ffffff', sw: 1.2, opacity: 0.55, transform: t });
    }
  }
  return parts;
}

/** Tấm đế: 3 tấm (giữa dài hơn), mỗi tấm một ô sao. */
function basePlates(tone: Tone, stars: number, lit: number): MedalPart[] {
  const center: Pt[] = [
    [46, 96],
    [74, 96],
    [74, 126],
    [60, 135],
    [46, 126],
  ];
  const side: Pt[] = [
    [18, 84],
    [44, 94],
    [44, 122],
    [22, 113],
  ];
  const parts: MedalPart[] = [];
  for (const pts of [side, mirror(side), center]) {
    parts.push({ d: roundedPath(pts, 2.5), fill: tone.dark, transform: 'translate(0 3)' });
    parts.push({ d: roundedPath(pts, 2.5), fill: tone.base });
  }
  // gờ sáng trên mép tấm
  parts.push({ d: 'M48 99H72', stroke: tone.light, sw: 1.6, opacity: 0.9 });
  const slots: Pt[] =
    stars === 1
      ? [[60, 122]]
      : [
          [32, 109],
          [60, 122],
          [88, 109],
        ];
  // thứ tự sáng: giữa trước, rồi trái, phải
  const order = stars === 1 ? [0] : [1, 0, 2];
  order.forEach((slot, idx) => {
    const [x, y] = slots[slot];
    const on = idx < lit;
    const r = slot === (stars === 1 ? 0 : 1) ? 6 : 5;
    parts.push({
      d: circle(r + 2.2, x, y),
      fill: mix(tone.dark, '#000000', 0.15),
      opacity: on ? 0.35 : 0.5,
    });
    parts.push({
      d: star(x, y, r),
      fill: on ? '#FFF3BF' : tone.dark,
      stroke: on ? '#C98A00' : undefined,
      sw: on ? 0.9 : undefined,
      opacity: on ? 1 : 0.7,
    });
  });
  return parts;
}

// ---------------------------------------------------------------- huy chương

export function buildMedal(o: MedalOptions): MedalPart[] {
  const locked = o.level === 0;
  const special = o.kind === 'SPECIAL';
  const sides = special ? 12 : 6;
  const metal: Tone = locked
    ? BADGE_NEUTRALS.locked
    : special
      ? SPECIAL_RIM
      : BADGE_METALS[o.level as 1 | 2 | 3];
  const cat = BADGE_CATEGORY_THEMES[o.category];
  const face: Tone = locked ? LOCKED_FACE : cat;
  const level = special ? (locked ? 0 : 3) : o.level; // đặc biệt: trang trí như cấp cao nhất
  const R = special ? 49 : 51;
  const parts: MedalPart[] = [];

  // Hào quang (cấp III / đặc biệt)
  if (level === 3) {
    parts.push({ d: circle(68), fill: metal.light, opacity: 0.16 });
    parts.push({ d: circle(58), fill: metal.light, opacity: 0.24 });
    for (let k = 0; k < 8; k++) {
      const a = (Math.PI / 4) * k + Math.PI / 8;
      const p = (r: number, da: number): Pt => [
        CX + r * Math.cos(a + da),
        CY + r * Math.sin(a + da),
      ];
      parts.push({
        d: poly([p(50, -0.12), p(72, 0), p(50, 0.12)]),
        fill: metal.light,
        opacity: 0.55,
      });
    }
  }
  if (level === 3 && !special) parts.push(...wings(metal));
  if (o.base !== false)
    parts.push(...basePlates(metal, special ? 1 : 3, special ? (locked ? 0 : 1) : o.level));
  if (level >= 2 && !special) parts.push(...sideSpikes(metal, CX - R * Math.cos(Math.PI / 6) + 1));

  // Khung: bóng khối, viền ngoài bo tròn, vòng mặt cắt
  const outer = roundedPath(vertices(sides, R + 2), 6);
  parts.push({ d: outer, fill: mix(metal.dark, '#000000', 0.2), transform: 'translate(0 4)' });
  parts.push({ d: outer, fill: metal.dark });
  parts.push(...facetRing(sides, R, R - 10, metal));
  parts.push({ d: poly(vertices(sides, R - 10.5)), stroke: metal.light, sw: 1.2, opacity: 0.9 });

  // Lòng lõm: viền tối, vòng mặt cắt chiếu ngược, mặt chính
  const rFace = R - 12;
  parts.push({ d: poly(vertices(sides, rFace)), fill: face.dark });
  parts.push(...facetRing(sides, rFace, rFace - 5, face, true));
  parts.push({
    d: poly(vertices(sides, rFace - 5)),
    fill: locked ? face.base : mix(face.base, face.dark, 0.25),
  });

  if (!locked) {
    const p = pattern(cat.pattern, rFace - 7);
    if (p.stroke) parts.push({ d: p.stroke, stroke: '#ffffff', sw: p.strokeWidth, opacity: 0.13 });
    if (p.fill) parts.push({ d: p.fill, fill: '#ffffff', opacity: 0.13 });
    parts.push({ d: circle(21), fill: face.light, opacity: 0.3 });
    parts.push({ d: circle(14), fill: face.light, opacity: 0.25 });
  }

  // Hình: bóng tối lệch xuống + nét trắng dày
  const glyph = o.hidden ? SECRET_GLYPH : BADGE_GLYPHS[o.code];
  const gT = 'translate(37 34.5) scale(1.92)';
  if (!locked)
    parts.push({
      d: glyph,
      stroke: face.dark,
      sw: 2.3,
      transform: 'translate(37 36.6) scale(1.92)',
      opacity: 0.9,
    });
  parts.push({
    d: glyph,
    stroke: locked ? BADGE_NEUTRALS.lockedInk : special ? SPECIAL_GLYPH : '#ffffff',
    sw: 2,
    transform: gT,
  });

  if (!locked) {
    // Ánh phản chiếu + lấp lánh
    parts.push({
      d: poly([
        [CX - 22, CY - 20],
        [CX - 6, CY - 28],
        [CX - 2, CY - 24],
        [CX - 19, CY - 15],
      ]),
      fill: '#ffffff',
      opacity: 0.16,
    });
    parts.push({ d: sparkle(CX - 17, CY - 14, 5), fill: '#ffffff' });
    parts.push({ d: sparkle(CX + 19, CY + 15, 3.2), fill: '#ffffff', opacity: 0.9 });
  }

  // Ngọc trên đỉnh: II nhỏ, III / đặc biệt lớn; màu ngọc = màu nhóm (đặc biệt: vàng)
  if (level >= 2) {
    const gemTone = special ? BADGE_METALS[3] : cat;
    const big = level === 3;
    parts.push(...gem(CY - R - (big ? 1 : 0), big ? 16 : 12, big ? 22 : 16, gemTone, metal));
  }

  // Ổ khóa
  if (locked) {
    parts.push({ d: circle(11, 97, 25), fill: '#ffffff', stroke: '#D4D0E3', sw: 1.5 });
    parts.push({
      d: 'M92.5 24h9v7h-9zM94.5 24v-2.4a2.5 2.5 0 0 1 5 0V24',
      stroke: '#5D6080',
      sw: 1.6,
    });
  }
  return parts;
}
