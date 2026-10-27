/* Pure logic. Node tests use module.exports; WebView uses window.BilLogic. */
(function (root) {
  'use strict';
  const EPS = 0.000000001;
  function requireValue(value, name) {
    if (value === '' || value === null || value === undefined || !Number.isFinite(Number(value)))
      throw new Error('Sila isi ' + name + '.');
    return Number(value);
  }
  function splitBill(v) {
    const month = String(v.month || '');
    if (!/^\d{4}-\d{2}$/.test(month)) throw new Error('Sila pilih bulan bil.');
    const total = requireValue(v.kwh, 'jumlah kWh bil utama');
    const amount = requireValue(v.payable, 'jumlah perlu dibayar');
    const zp = requireValue(v.zprev, 'bacaan Zul sebelum');
    const zc = requireValue(v.zcurr, 'bacaan Zul terkini');
    const sp = requireValue(v.sprev, 'bacaan Saldi sebelum');
    const sc = requireValue(v.scurr, 'bacaan Saldi terkini');
    if (total <= 0) throw new Error('Jumlah kWh mesti lebih daripada sifar.');
    if ([amount, zp, zc, sp, sc].some(n => n < 0)) throw new Error('Nilai tidak boleh negatif.');
    if (zc < zp || sc < sp) throw new Error('Bacaan terkini tidak boleh kurang daripada bacaan sebelum.');
    if (!Number.isSafeInteger(Math.round(total * 1000))) throw new Error('Nilai kWh terlalu besar.');
    const totalUnits = Math.round(total * 1000);
    const zulUnits = Math.round(zc * 1000) - Math.round(zp * 1000);
    const saldiUnits = Math.round(sc * 1000) - Math.round(sp * 1000);
    let ownerUnits = totalUnits - zulUnits - saldiUnits;
    if (ownerUnits < 0) throw new Error('Penggunaan Zul + Saldi melebihi kWh bil utama. Semak bacaan.');
    const cents = Math.round((amount + Number.EPSILON) * 100);
    if (!Number.isSafeInteger(cents)) throw new Error('Amaun terlalu besar.');
    let zcents = Math.round(cents * zulUnits / totalUnits);
    let scents = Math.round(cents * saldiUnits / totalUnits);
    while (zcents + scents > cents) {
      // Rare 1-cent rounding overflow: remove the most over-rounded allocation.
      const zExcess = zcents - cents * zulUnits / totalUnits;
      const sExcess = scents - cents * saldiUnits / totalUnits;
      if (zExcess >= sExcess) zcents--; else scents--;
    }
    const ocents = cents - zcents - scents;
    return {
      month, kwh: totalUnits / 1000, payable: cents / 100,
      zprev: zp, zcurr: zc, sprev: sp, scurr: sc,
      z: zulUnits / 1000, s: saldiUnits / 1000, owner: ownerUnits / 1000,
      zrm: zcents / 100, srm: scents / 100, orm: ocents / 100
    };
  }

  function parseNumber(piece) {
    if (piece == null) return null;
    let str = String(piece).trim().replace(/\s/g, '').replace(/^RM/i, '');
    if (!/^\d[\d,.]*$/.test(str)) return null;
    const lastDot = str.lastIndexOf('.'), lastComma = str.lastIndexOf(',');
    if (lastDot !== -1 && lastComma !== -1) {
      const decimalAt = Math.max(lastDot, lastComma);
      str = str.slice(0, decimalAt).replace(/[,.]/g, '') + '.' + str.slice(decimalAt + 1);
    } else if (lastComma !== -1) {
      const a = str.split(',');
      if (a.length === 2 && a[1].length > 0 && a[1].length <= 2) str = a[0] + '.' + a[1];
      else str = str.replace(/,/g, '');
    } else if (lastDot !== -1) {
      const a = str.split('.');
      if (a.length > 2) str = a.slice(0, -1).join('') + '.' + a[a.length - 1];
    }
    const n = Number(str);
    return Number.isFinite(n) && n >= 0 && n <= 10000000 ? n : null;
  }

  function candidates(raw) {
    const out = [];
    const seen = new Set();
    // Strip stray spaces between digits but keep line context. Excludes words, preserves decimals.
    const texts = String(raw || '').replace(/(\d)\s+(?=\d{3}\b)/g, '$1').split(/\r?\n/);
    for (const line of texts) {
      const matches = line.match(/(?:RM\s*)?\d[\d,.]*/gi) || [];
      for (const match of matches) {
        const n = parseNumber(match);
        if (n === null) continue;
        const k = n.toFixed(3);
        if (!seen.has(k)) { seen.add(k); out.push({value:n, sample:match.trim(), context:line.trim()}); }
      }
    }
    return out.slice(0, 28);
  }
  function bestBillGuesses(raw) {
    const lines = String(raw || '').split(/\r?\n/).map(x => x.trim()).filter(Boolean);
    const kwhRegex = /(?:TOTAL\s*(?:USAGE|CONSUMPTION|KWH)|JUMLAH\s*(?:PENGGUNAAN|UNIT)|PENGGUNAAN\s*(?:BIL|TENAGA)|\bKWH\b|BILLING\s*UNITS)/i;
    const payableRegex = /(?:JUMLAH\s*(?:PERLU\s*)?DIBAYAR|AMOUNT\s*(?:DUE|PAYABLE)|TOTAL\s*(?:TO\s*PAY|PAYABLE|DUE)|BAKI\s*PERLU\s*DIBAYAR)/i;
    function lookFor(pattern, forKwh) {
      for (let i = 0; i < lines.length; i++) {
        if (!pattern.test(lines[i])) continue;
        const pool = [lines[i], lines[i + 1] || ''];
        for (let offset = 0; offset < pool.length; offset++) {
          const matches = pool[offset].match(/(?:RM\s*)?\d[\d,.]*/gi) || [];
          // If units are mentioned in the same line, often the last numeric token is usage/amount.
          const matchesOrdered = offset === 0 ? matches.slice().reverse() : matches;
          for (const m of matchesOrdered) {
            const n = parseNumber(m);
            if (n === null) continue;
            if (forKwh && n <= 0) continue;
            if (forKwh && /RM\s*\d/i.test(m)) continue;
            return n;
          }
        }
      }
      return null;
    }
    return { kwh: lookFor(kwhRegex, true), payable: lookFor(payableRegex, false) };
  }
  const api = {splitBill, candidates, parseNumber, bestBillGuesses};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.BilLogic = api;
})(typeof window !== 'undefined' ? window : globalThis);
