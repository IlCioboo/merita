// core.js — citirea textului comenzii + calculul profitului.
// Nu depinde de pagina, ca sa poata fi testat separat.

(function (global) {
  'use strict';

  const NUM = '(\\d{1,4}(?:[.,]\\d{1,2})?)';

  function toNumber(s) {
    return parseFloat(String(s).replace(',', '.'));
  }

  function detectPlatform(text) {
    const t = text.toLowerCase();
    if (t.includes('glovo')) return 'Glovo';
    if (t.includes('wolt')) return 'Wolt';
    if (t.includes('uber')) return 'Uber Eats';
    if (t.includes('bolt')) return 'Bolt Food';
    return '';
  }

  // Ia toate valorile dintr-un regex, retinand si linia pe care apar
  function collect(lines, regex, mapFn) {
    const out = [];
    lines.forEach((line, i) => {
      regex.lastIndex = 0;
      let m;
      while ((m = regex.exec(line)) !== null) {
        const v = mapFn(m);
        if (v != null && isFinite(v) && v > 0) out.push({ value: v, line: i, text: line.trim() });
      }
    });
    return out;
  }

  const TOTAL_WORDS = /(total|în total|in total|overall|traseu|trip)/i;

  const MONEY_TEST = /(?:\b(?:lei|ron|eur)\b|€)\s*\d|\d\s*(?:\b(?:lei|ron|eur)\b|€)/i;

  // Daca exista o valoare marcata "total" (sau pe acelasi rand cu suma, ca butonul Bolt
  // "24 min, 22,73 RON"), o folosim. Daca cea mai mare e suma celorlalte (11 + 13 = 24),
  // e totalul. Altfel adunam tot (ridicare + livrare).
  function totalOrSum(items, lines) {
    if (!items.length) return null;
    const marked = items.filter(it =>
      TOTAL_WORDS.test(lines[it.line]) ||
      MONEY_TEST.test(lines[it.line]) ||
      (lines[it.line - 1] && TOTAL_WORDS.test(lines[it.line - 1]) && lines[it.line - 1].length < 25));
    if (marked.length) return Math.max(...marked.map(it => it.value));
    const sum = items.reduce((s, it) => s + it.value, 0);
    if (items.length >= 3) {
      const top = Math.max(...items.map(it => it.value));
      if (Math.abs(top - (sum - top)) <= Math.max(1, top * 0.06)) return top;
    }
    return sum;
  }

  function parseOrder(rawText) {
    const text = String(rawText || '')
      .replace(/ /g, ' ')
      .replace(/[€]/g, ' € ');
    // Scoatem rândurile din propriile noastre notificări, dacă au intrat în captură
    const OWN = /(RON\/km|RON\/h|MERITĂ|LA LIMITĂ|Am citit|Nu am găsit)/i;
    const lines = text.split(/\r?\n/).filter(l => !OWN.test(l));

    // Bani: "12,50 lei", "RON 18.45", "15 €"
    const moneyRe = new RegExp(
      '(?:\\b(?:lei|ron|eur)\\b|€)\\s*' + NUM + '|' + NUM + '\\s*(?:\\b(?:lei|ron|eur)\\b|€)', 'gi');
    // Ignoram castigul zilei / soldul si sumele prea mari pentru o comanda
    const notOrder = /(azi|astăzi|astazi|today|săptămân|saptaman|week|sold|balance|câștigat|castigat|earned)/i;
    const okMoney = x => x.value <= 300 && !notOrder.test(lines[x.line]);
    let money = collect(lines, moneyRe, m => toNumber(m[1] || m[2])).filter(okMoney);
    // Rezervă: nicio sumă cu lei/RON → luăm numere cu 2 zecimale (ex. 14,50) care nu sunt km/min/oră
    if (!money.length) {
      const bareRe = /(^|[^\d:.,])(\d{1,3}[.,]\d{2})(?![\d:.,])(?!\s*(?:km\b|m\b|min))/gi;
      money = collect(lines, bareRe, m => toNumber(m[2])).filter(okMoney);
    }

    // Distante: "3,2 km", "800 m"
    const kmRe = new RegExp(NUM + '\\s*km\\b', 'gi');
    const mRe = /(\d{2,4})\s*m\b(?!in)/gi;
    const kms = collect(lines, kmRe, m => toNumber(m[1]))
      .concat(collect(lines, mRe, m => toNumber(m[1]) / 1000));

    // Timp: "15 min", "1 h 10 min", "1 oră 5 min"
    const timeRe = /(?:(\d{1,2})\s*(?:h|ore|oră|ora|hr)\s*)?(\d{1,3})\s*(?:min|minute|mins)\b/gi;
    const times = collect(lines, timeRe, m => (m[1] ? parseInt(m[1], 10) * 60 : 0) + parseInt(m[2], 10));

    return {
      platform: detectPlatform(text),
      // castigul = cea mai mare suma (de obicei totalul cu tot cu bacsis)
      earnings: money.length ? Math.max(...money.map(x => x.value)) : null,
      km: kms.length ? round(totalOrSum(kms, lines), 2) : null,
      minutes: times.length ? totalOrSum(times, lines) : null,
      found: { money, kms, times },
    };
  }

  // Preset-uri de vehicul (valori aproximative, se pot modifica in setari)
  const VEHICLES = {
    benzina:   { label: 'Mașină benzină',     unit: 'l',   consumption: 7,   price: 7.5, maint: 0.15, speed: 25 },
    motorina:  { label: 'Mașină motorină',    unit: 'l',   consumption: 6,   price: 7.6, maint: 0.15, speed: 25 },
    gpl:       { label: 'Mașină GPL',         unit: 'l',   consumption: 9,   price: 3.6, maint: 0.15, speed: 25 },
    electrica: { label: 'Mașină electrică',   unit: 'kWh', consumption: 16,  price: 1.5, maint: 0.08, speed: 25 },
    scuter:    { label: 'Scuter / moto',      unit: 'l',   consumption: 3,   price: 7.5, maint: 0.08, speed: 25 },
    ebike:     { label: 'Bicicletă electrică',unit: 'kWh', consumption: 1.5, price: 1.2, maint: 0.03, speed: 18 },
    bicicleta: { label: 'Bicicletă',          unit: 'l',   consumption: 0,   price: 0,   maint: 0.02, speed: 15 },
  };

  const DEFAULT_SETTINGS = {
    vehicle: 'benzina',
    consumption: 7,      // l sau kWh la 100 km
    price: 7.5,          // RON per l / kWh
    maint: 0.15,         // RON/km: uzura, anvelope, revizii
    speed: 25,           // km/h medie, cand aplicatia nu arata timpul
    waitMin: 5,          // minute de asteptare la restaurant
    returnPct: 0,        // % km in plus pentru drumul de intoarcere
    commissionPct: 0,    // % comision flota / taxe din castig
    minPerKm: 1.5,       // prag RON net / km
    minPerHour: 30,      // prag RON net / ora
  };

  function calculate(order, s) {
    const earnings = Number(order.earnings) || 0;
    const hasTime = Number(order.minutes) > 0;
    // Fara km pe ecran (Bolt Food): ii estimam din minute cu viteza medie din setari
    const estimatedKm = !(Number(order.km) > 0) && hasTime;
    const km = estimatedKm
      ? Number(order.minutes) / 60 * Math.max(Number(s.speed) || 1, 1)
      : (Number(order.km) || 0);
    const kmTotal = km * (1 + (Number(s.returnPct) || 0) / 100);
    const costPerKm = (Number(s.consumption) || 0) / 100 * (Number(s.price) || 0) + (Number(s.maint) || 0);
    const driveMin = Number(order.minutes) > 0
      ? Number(order.minutes)
      : (kmTotal / Math.max(Number(s.speed) || 1, 1)) * 60;
    const minutes = driveMin + (Number(s.waitMin) || 0);

    const gross = earnings * (1 - (Number(s.commissionPct) || 0) / 100);
    const cost = kmTotal * costPerKm;
    const net = gross - cost;
    const perKm = kmTotal > 0 ? net / kmTotal : null;
    const perHour = minutes > 0 ? net / (minutes / 60) : null;

    // Pretul minim: profitul cerut de praguri + costul drumului, inainte de comision
    const needNet = Math.max((Number(s.minPerKm) || 0) * kmTotal, (Number(s.minPerHour) || 0) * minutes / 60);
    const keep = 1 - (Number(s.commissionPct) || 0) / 100;
    const minEarnings = keep > 0.01 ? (needNet + cost) / keep : 0;

    const okKm = perKm != null && perKm >= s.minPerKm;
    const okHour = perHour != null && perHour >= s.minPerHour;
    let verdict = 'bad';
    if (okKm && okHour) verdict = 'good';
    else if (okKm || okHour) verdict = 'meh';

    return {
      kmTotal: round(kmTotal, 2),
      minutes: Math.round(minutes),
      costPerKm: round(costPerKm, 2),
      cost: round(cost, 2),
      gross: round(gross, 2),
      net: round(net, 2),
      perKm: perKm == null ? null : round(perKm, 2),
      perHour: perHour == null ? null : round(perHour, 1),
      verdict,
      estimatedTime: !hasTime,
      estimatedKm,
      minEarnings: round(minEarnings, 2),
    };
  }

  function round(v, d) {
    const f = Math.pow(10, d);
    return Math.round(v * f) / f;
  }

  global.Core = { parseOrder, calculate, VEHICLES, DEFAULT_SETTINGS };
})(typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : self));
