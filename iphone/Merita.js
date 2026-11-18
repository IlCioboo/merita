// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: green; icon-glyph: car;

// Merita - calculator profit curse Glovo / Wolt / Uber Eats (fiÈ™ier generat de build.ps1)
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

  // Daca exista o valoare marcata ca "total", o folosim; altfel adunam tot
  function totalOrSum(items, lines) {
    if (!items.length) return null;
    const marked = items.filter(it =>
      TOTAL_WORDS.test(lines[it.line]) ||
      (lines[it.line - 1] && TOTAL_WORDS.test(lines[it.line - 1]) && lines[it.line - 1].length < 25));
    if (marked.length) return Math.max(...marked.map(it => it.value));
    return items.reduce((s, it) => s + it.value, 0);
  }

  function parseOrder(rawText) {
    const text = String(rawText || '')
      .replace(/ /g, ' ')
      .replace(/[€]/g, ' € ');
    const lines = text.split(/\r?\n/);

    // Bani: "12,50 lei", "RON 18.45", "15 €"
    const moneyRe = new RegExp(
      '(?:\\b(?:lei|ron|eur)\\b|€)\\s*' + NUM + '|' + NUM + '\\s*(?:\\b(?:lei|ron|eur)\\b|€)', 'gi');
    // Ignoram castigul zilei / soldul si sumele prea mari pentru o comanda
    const notOrder = /(azi|astăzi|astazi|today|săptămân|saptaman|week|sold|balance|câștigat|castigat|earned)/i;
    const money = collect(lines, moneyRe, m => toNumber(m[1] || m[2]))
      .filter(x => x.value <= 300 && !notOrder.test(lines[x.line]));

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
    const km = Number(order.km) || 0;
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
      estimatedTime: !(Number(order.minutes) > 0),
    };
  }

  function round(v, d) {
    const f = Math.pow(10, d);
    return Math.round(v * f) / f;
  }

  global.Core = { parseOrder, calculate, VEHICLES, DEFAULT_SETTINGS };
})(typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : self));

// ===== Partea de iPhone (Scriptable) =====
// Rulat din Scurtătură: primește textul de pe ecran și întoarce verdictul (pentru notificare).
// Rulat direct în Scriptable: meniu cu setări vehicul, praguri, test, istoric.

const fm = FileManager.local();
const DIR = fm.documentsDirectory();
const SETTINGS_FILE = fm.joinPath(DIR, 'merita-settings.json');
const HISTORY_FILE = fm.joinPath(DIR, 'merita-history.json');
const LAST_FILE = fm.joinPath(DIR, 'merita-last.txt');

function readJson(path, fallback) {
  try { return fm.fileExists(path) ? JSON.parse(fm.readString(path)) : fallback; } catch (e) { return fallback; }
}
function writeJson(path, v) { fm.writeString(path, JSON.stringify(v)); }

function loadSettings() { return Object.assign({}, Core.DEFAULT_SETTINGS, readJson(SETTINGS_FILE, {})); }
function saveSettings(s) { writeJson(SETTINGS_FILE, s); }

const VERDICT = { good: 'MERITĂ ✅', meh: 'LA LIMITĂ ⚠️', bad: 'NU MERITĂ ❌' };
const f2 = v => Number(v).toFixed(2);

function summary(order, s) {
  if (order.earnings == null || order.km == null) {
    const miss = [order.earnings == null ? 'câștigul' : '', order.km == null ? 'distanța' : ''].filter(Boolean).join(' și ');
    return { ok: false, text: '❓ Nu am găsit ' + miss + '.' };
  }
  const r = Core.calculate(order, s);
  const text =
    VERDICT[r.verdict] + (order.platform ? ' · ' + order.platform : '') + '\n' +
    'net ' + f2(r.net) + ' RON · ' + f2(r.perKm) + ' RON/km · ' + Math.round(r.perHour) + ' RON/h\n' +
    f2(order.earnings) + ' RON · ' + r.kmTotal + ' km · ~' + r.minutes + ' min' + (r.estimatedTime ? ' (est.)' : '') +
    ' · cost ' + f2(r.cost);
  return { ok: true, text, r };
}

function addHistory(order, r) {
  const h = readJson(HISTORY_FILE, []);
  h.unshift({ t: Date.now(), p: order.platform, e: order.earnings, km: order.km, net: r.net, v: r.verdict });
  writeJson(HISTORY_FILE, h.slice(0, 50));
}

// Textul poate veni ca text, listă sau obiect (depinde de versiunea iOS / Scurtături):
// adunăm toate bucățile de text, oricum ar fi împachetate.
let inputKind = '';
function inputText() {
  const p = args.shortcutParameter;
  const src = p != null ? p : (args.plainTexts || []);
  inputKind = Array.isArray(src) ? 'listă' : typeof src;
  const parts = [];
  const add = (v, depth) => {
    if (v == null || depth > 6) return;
    if (typeof v === 'string' || typeof v === 'number') parts.push(String(v));
    else if (Array.isArray(v)) v.forEach(x => add(x, depth + 1));
    else if (typeof v === 'object') Object.keys(v).forEach(k => add(v[k], depth + 1));
  };
  add(src, 0);
  return parts.join('\n');
}

function preview(text) {
  const t = text.replace(/\s*\n\s*/g, ' | ').trim();
  return t.length > 160 ? t.slice(0, 160) + '…' : t;
}

// ---------- Meniul din aplicație ----------
async function chooseVehicle(s) {
  const a = new Alert();
  a.title = 'Vehicul';
  a.message = 'Acum: ' + (Core.VEHICLES[s.vehicle] || {}).label + '\nAlegerea pune valori tipice; le poți ajusta după.';
  const keys = Object.keys(Core.VEHICLES);
  keys.forEach(k => a.addAction(Core.VEHICLES[k].label));
  a.addCancelAction('Înapoi');
  const i = await a.present();
  if (i < 0) return;
  const v = Core.VEHICLES[keys[i]];
  s.vehicle = keys[i];
  ['consumption', 'price', 'maint', 'speed'].forEach(k => { s[k] = v[k]; });
  saveSettings(s);
  await editNumbers(s, 'Costuri vehicul', [
    ['consumption', 'Consum ' + v.unit + '/100 km'],
    ['price', 'Preț RON/' + v.unit],
    ['maint', 'Uzură RON/km (anvelope, revizii)'],
    ['speed', 'Viteză medie km/h'],
  ]);
}

async function editNumbers(s, title, fields) {
  const a = new Alert();
  a.title = title;
  a.message = 'Câmpurile, în ordine:\n' + fields.map((f, i) => (i + 1) + '. ' + f[1]).join('\n');
  fields.forEach(f => a.addTextField(f[1], String(s[f[0]])).setDecimalPadKeyboard());
  a.addAction('Salvează');
  a.addCancelAction('Renunță');
  if (await a.present() < 0) return;
  fields.forEach((f, i) => {
    const v = parseFloat(String(a.textFieldValue(i)).replace(',', '.'));
    if (isFinite(v)) s[f[0]] = v;
  });
  saveSettings(s);
}

async function showMessage(title, msg) {
  const a = new Alert();
  a.title = title; a.message = msg;
  a.addAction('OK');
  await a.present();
}

async function menu() {
  const s = loadSettings();
  while (true) {
    const a = new Alert();
    a.title = 'Merită cursa?';
    a.message = (Core.VEHICLES[s.vehicle] || {}).label + ' · cost ' +
      f2(s.consumption / 100 * s.price + s.maint) + ' RON/km\nMinim ' + s.minPerKm + ' RON/km și ' + s.minPerHour + ' RON/oră';
    const items = [
      ['Vehicul și costuri', () => chooseVehicle(s)],
      ['Praguri și ajustări', () => editNumbers(s, 'Praguri și ajustări', [
        ['minPerKm', 'Minim RON net / km'],
        ['minPerHour', 'Minim RON net / oră'],
        ['waitMin', 'Așteptare la restaurant (min)'],
        ['returnPct', 'Km retur în plus (%)'],
        ['commissionPct', 'Comision flotă / taxe (%)'],
      ])],
      ['Testează cu textul din clipboard', async () => {
        const t = Pasteboard.paste() || '';
        await showMessage('Test', t ? summary(Core.parseOrder(t), s).text : 'Clipboard-ul e gol.');
      }],
      ['Ultimul text citit', async () => {
        const t = fm.fileExists(LAST_FILE) ? fm.readString(LAST_FILE) : '';
        const b = new Alert();
        b.title = 'Ultimul text citit';
        b.message = t ? t.slice(0, 1500) : 'Nimic citit încă.';
        b.addAction('Copiază (ca să mi-l trimiți)');
        b.addCancelAction('Închide');
        if (await b.present() === 0) Pasteboard.copy(t);
      }],
      ['Istoric', async () => {
        const h = readJson(HISTORY_FILE, []);
        const dot = { good: '🟢', meh: '🟡', bad: '🔴' };
        const lines = h.slice(0, 20).map(x => {
          const d = new Date(x.t);
          const hh = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
          return dot[x.v] + ' ' + d.getDate() + '.' + (d.getMonth() + 1) + ' ' + hh + ' ' + (x.p || '') +
            ' ' + x.e + ' RON, ' + x.km + ' km, net ' + x.net;
        });
        await showMessage('Istoric', lines.length ? lines.join('\n') : 'Nimic încă.');
      }],
    ];
    items.forEach(it => a.addAction(it[0]));
    a.addCancelAction('Închide');
    const i = await a.present();
    if (i < 0) break;
    await items[i][1]();
  }
}

// ---------- Pornire ----------
async function main() {
  if (config.runsInApp && args.shortcutParameter == null) {
    await menu();
    return;
  }
  const text = inputText();
  if (!text.trim()) {
    Script.setShortcutOutput('❓ Nu am primit text. Verifică Scurtătura: Captură de ecran → Extrage text → Rulează scriptul.');
    return;
  }
  fm.writeString(LAST_FILE, '[primit: ' + inputKind + ']\n' + text);
  const s = loadSettings();
  const order = Core.parseOrder(text);
  const res = summary(order, s);
  if (res.ok) addHistory(order, res.r);
  Script.setShortcutOutput(res.ok ? res.text : res.text + '\nAm citit (' + inputKind + '): ' + preview(text));
}

await main();
Script.complete();
