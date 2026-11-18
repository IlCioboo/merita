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
