import { CategoryTheme } from '../../../core/models/badge.models';

/*
 * [THÊM] Hình học của huy chương, tính sẵn thành chuỗi path SVG (hệ tọa độ 120 × 128).
 * Không dùng gradient / clipPath (url(#id) dễ hỏng khi app có <base href> và nhiều huy chương cùng trang):
 * độ khối tạo bằng các lớp màu phẳng light / base / dark chồng lên nhau, kiểu flat illustration.
 */

export const CX = 60;
export const CY = 58;

type Point = [number, number];

const f = (n: number) => n.toFixed(2);

/** Đa giác bo góc: mỗi đỉnh thay bằng một đường cong bậc 2, bán kính bo `corner`. */
export function roundedPath(points: Point[], corner: number): string {
  const n = points.length;
  const parts: string[] = [];
  for (let i = 0; i < n; i++) {
    const [x, y] = points[i];
    const [px, py] = points[(i - 1 + n) % n];
    const [nx, ny] = points[(i + 1) % n];
    const dPrev = Math.hypot(px - x, py - y);
    const dNext = Math.hypot(nx - x, ny - y);
    const a = Math.min(corner / dPrev, 0.5);
    const b = Math.min(corner / dNext, 0.5);
    const p1: Point = [x + (px - x) * a, y + (py - y) * a];
    const p2: Point = [x + (nx - x) * b, y + (ny - y) * b];
    parts.push(`${i === 0 ? 'M' : 'L'}${f(p1[0])} ${f(p1[1])}Q${f(x)} ${f(y)} ${f(p2[0])} ${f(p2[1])}`);
  }
  return parts.join('') + 'Z';
}

/** Lục giác đỉnh nhọn hướng lên (dáng "viên đá quý" / khiên), tâm (cx, cy). */
export function hexagon(r: number, corner: number, cx = CX, cy = CY): string {
  const pts = Array.from({ length: 6 }, (_, i): Point => {
    const a = (Math.PI / 3) * i - Math.PI / 2;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  });
  return roundedPath(pts, corner);
}

/** Con dấu răng cưa (huy hiệu đặc biệt): `teeth` răng, bán kính ngoài / trong. */
export function seal(teeth: number, outer: number, inner: number, cx = CX, cy = CY): string {
  const pts = Array.from({ length: teeth * 2 }, (_, i): Point => {
    const a = (Math.PI / teeth) * i - Math.PI / 2;
    const r = i % 2 === 0 ? outer : inner;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  });
  return roundedPath(pts, 3);
}

export function circle(r: number, cx = CX, cy = CY): string {
  return `M${f(cx - r)} ${f(cy)}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0Z`;
}

/** Ngôi sao 5 cánh tâm (cx, cy). */
export function star(cx: number, cy: number, r: number): string {
  const pts = Array.from({ length: 10 }, (_, i): Point => {
    const a = (Math.PI / 5) * i - Math.PI / 2;
    const rr = i % 2 === 0 ? r : r * 0.46;
    return [cx + rr * Math.cos(a), cy + rr * Math.sin(a)];
  });
  return roundedPath(pts, 0.6);
}

/** Lấp lánh 4 cánh. */
function sparkle(cx: number, cy: number, r: number): string {
  const k = r * 0.28;
  return `M${f(cx)} ${f(cy - r)}Q${f(cx + k)} ${f(cy - k)} ${f(cx + r)} ${f(cy)}Q${f(cx + k)} ${f(cy + k)} ${f(cx)} ${f(cy + r)}Q${f(cx - k)} ${f(cy + k)} ${f(cx - r)} ${f(cy)}Q${f(cx - k)} ${f(cy - k)} ${f(cx)} ${f(cy - r)}Z`;
}

/** Dây cung của đường tròn bán kính r cắt bởi đường thẳng cách tâm d theo pháp tuyến (nx, ny). */
function chord(d: number, nx: number, ny: number, r: number): string {
  const half = Math.sqrt(Math.max(r * r - d * d, 0));
  const tx = -ny;
  const ty = nx;
  const mx = CX + d * nx;
  const my = CY + d * ny;
  return `M${f(mx - tx * half)} ${f(my - ty * half)}L${f(mx + tx * half)} ${f(my + ty * half)}`;
}

export interface PatternPaths {
  /** Vẽ bằng nét (stroke) */
  stroke: string;
  strokeWidth: number;
  /** Vẽ bằng mảng (fill) */
  fill: string;
}

/**
 * Họa tiết nền riêng mỗi nhóm, nằm trọn trong vòng tròn bán kính r (lọt trong mặt huy chương),
 * vẽ màu trắng mờ. Nhìn lướt cả lưới huy hiệu vẫn phân biệt được nhóm, kể cả với người khó phân biệt màu.
 */
export function pattern(kind: CategoryTheme['pattern'], r: number): PatternPaths {
  const empty: PatternPaths = { stroke: '', strokeWidth: 0, fill: '' };
  const inside = (x: number, y: number, pad = 0) => Math.hypot(x - CX, y - CY) <= r - pad;
  switch (kind) {
    case 'stripes': {
      const n = Math.SQRT1_2;
      const d = [-28, -18, -8, 2, 12, 22].map((o) => chord(o, n, -n, r)).join('');
      return { ...empty, stroke: d, strokeWidth: 4 };
    }
    case 'lines': {
      const d = [-24, -16, -8, 0, 8, 16, 24].map((o) => chord(o, 0, 1, r)).join('');
      return { ...empty, stroke: d, strokeWidth: 1.6 };
    }
    case 'rings':
      return { ...empty, stroke: [11, 19, 27].map((rr) => circle(rr)).join(''), strokeWidth: 2.2 };
    case 'rays': {
      const d = Array.from({ length: 16 }, (_, i) => {
        const a = (Math.PI / 8) * i;
        return `M${f(CX + 9 * Math.cos(a))} ${f(CY + 9 * Math.sin(a))}L${f(CX + r * Math.cos(a))} ${f(CY + r * Math.sin(a))}`;
      }).join('');
      return { ...empty, stroke: d, strokeWidth: 2.6 };
    }
    case 'dots': {
      const pts: string[] = [];
      for (let x = CX - 32; x <= CX + 32; x += 8) {
        for (let y = CY - 32; y <= CY + 32; y += 8) {
          if (inside(x, y, 3)) pts.push(circle(1.5, x, y));
        }
      }
      return { ...empty, fill: pts.join('') };
    }
    case 'checks': {
      const sq: string[] = [];
      for (let x = CX - 35; x < CX + 35; x += 7) {
        for (let y = CY - 35; y < CY + 35; y += 7) {
          const odd = (Math.round((x - CX) / 7) + Math.round((y - CY) / 7)) % 2 === 0;
          if (odd && inside(x + 3.5, y + 3.5, 5)) sq.push(`M${f(x)} ${f(y)}h7v7h-7z`);
        }
      }
      return { ...empty, fill: sq.join('') };
    }
    case 'zigzag': {
      const rows = [-20, -6, 8, 22].map((oy) => {
        const y = CY + oy;
        const half = Math.sqrt(Math.max(r * r - oy * oy, 0)) - 3;
        let d = '';
        let up = true;
        for (let x = CX - half; x <= CX + half; x += 6) {
          d += `${d ? 'L' : 'M'}${f(x)} ${f(y + (up ? -3 : 3))}`;
          up = !up;
        }
        return d;
      });
      return { ...empty, stroke: rows.join(''), strokeWidth: 2 };
    }
    case 'embers': {
      const spots: [number, number, number][] = [
        [-22, 18, 3], [-14, 26, 2], [20, 20, 3.2], [26, 6, 2], [-26, 0, 2.2], [12, 28, 2], [-6, -26, 1.8], [22, -18, 2.4],
        [-20, -16, 2.6], [4, 30, 1.6],
      ];
      return { ...empty, fill: spots.map(([dx, dy, rr]) => circle(rr, CX + dx, CY + dy)).join('') };
    }
    case 'stars': {
      const spots: [number, number, number][] = [
        [-22, -14, 4], [20, -20, 3], [24, 14, 4.5], [-24, 16, 3], [-8, -28, 2.5], [8, 28, 2.5], [28, -2, 2],
      ];
      return { ...empty, fill: spots.map(([dx, dy, rr]) => sparkle(CX + dx, CY + dy, rr)).join('') };
    }
  }
}
