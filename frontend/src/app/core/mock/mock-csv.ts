/**
 * Đọc CSV cho chế độ MOCK (thay Apache Commons CSV của backend).
 * Hỗ trợ: dấu phẩy, ô bao "..." (có dấu phẩy / xuống dòng bên trong), "" = một dấu ", CRLF, BOM của Excel.
 */
export interface CsvTable {
  header: string[];
  /** Mỗi dòng dữ liệu: tên cột (viết thường) → giá trị đã trim. Dòng trống bị bỏ. */
  rows: Record<string, string>[];
}

export function parseCsv(text: string): CsvTable {
  const records = splitRecords(text.replace(/^﻿/, ''));
  const nonEmpty = records.filter((r) => r.some((cell) => cell.trim() !== ''));
  if (nonEmpty.length === 0) {
    return { header: [], rows: [] };
  }
  const header = nonEmpty[0].map((h) => h.trim().toLowerCase());
  const rows = nonEmpty.slice(1).map((cells) => {
    const row: Record<string, string> = {};
    header.forEach((name, i) => (row[name] = (cells[i] ?? '').trim()));
    return row;
  });
  return { header, rows };
}

function splitRecords(text: string): string[][] {
  const records: string[][] = [];
  let record: string[] = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        cell += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ',') {
      record.push(cell);
      cell = '';
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      record.push(cell);
      records.push(record);
      record = [];
      cell = '';
    } else {
      cell += c;
    }
  }
  if (cell !== '' || record.length > 0) {
    record.push(cell);
    records.push(record);
  }
  return records;
}
