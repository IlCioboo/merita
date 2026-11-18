// journal.js — cursele salvate, turele, statisticile, backup-ul și exportul Excel.
// Aceleași formate ca aplicația Android, ca un backup să meargă pe ambele telefoane.
// Nu depinde de pagină (doar de un „storage” cu getItem/setItem), ca să poată fi testat separat.

(function (global) {
  'use strict';

  const OFFER = 'offer', ACCEPTED = 'accepted', REJECTED = 'rejected';
  const MAX_SHIFT_MS = 16 * 3600000; // o tură uitată pornită nu numără mai mult de atât
  const WEEKDAYS = ['Luni', 'Marți', 'Miercuri', 'Joi', 'Vineri', 'Sâmbătă', 'Duminică'];
  const K_TRIPS = 'merita.trips', K_SHIFTS = 'merita.shifts', K_OLD = 'merita.history';

  function create(storage) {
    function load(key, fallback) {
      try { const v = JSON.parse(storage.getItem(key)); return v == null ? fallback : v; }
      catch (e) { return fallback; }
    }
    function save(key, v) { try { storage.setItem(key, JSON.stringify(v)); } catch (e) {} }

    // Istoricul vechi (cele salvate cu „Salvează în istoric”) devine curse luate
    (function migrate() {
      const old = load(K_OLD, null);
      if (!old || !old.length) return;
      const trips = load(K_TRIPS, []);
      const ids = new Set(trips.map(t => t.id));
      old.forEach(x => {
        if (ids.has(x.t)) return;
        trips.push({
          id: x.t, time: x.t, platform: x.p || '', earnings: +x.e || 0, km: +x.km || 0, minutes: 0,
          commission: 0, cost: Math.max(0, (+x.e || 0) - (+x.net || 0)), net: +x.net || 0,
          verdict: String(x.v || '').toUpperCase(), status: ACCEPTED,
        });
      });
      save(K_TRIPS, trips);
      storage.removeItem ? storage.removeItem(K_OLD) : save(K_OLD, []);
    })();

    // ---- Curse ----
    const trips = {
      all: () => load(K_TRIPS, []),
      saveAll: list => save(K_TRIPS, list),
      /** order = {platform, earnings, ...}, r = rezultatul din Core.calculate */
      add(order, r, status, time) {
        const t = time || Date.now();
        const list = trips.all();
        const id = list.some(x => x.id === t) ? t + 1 : t;
        const trip = {
          id, time: t, platform: order.platform || '', earnings: +order.earnings || 0,
          km: r.kmTotal, minutes: r.minutes, commission: round((+order.earnings || 0) - r.gross, 2),
          cost: r.cost, net: r.net, verdict: String(r.verdict).toUpperCase(), status: status || OFFER,
        };
        list.push(trip);
        trips.saveAll(list);
        return trip;
      },
      setStatus(id, status) {
        trips.saveAll(trips.all().map(t => (t.id === id ? Object.assign({}, t, { status }) : t)));
      },
      deleteMany(ids) {
        const set = new Set(ids);
        trips.saveAll(trips.all().filter(t => !set.has(t.id)));
      },
      /** Unește cu ce există deja (după id); întoarce câte erau noi. */
      importTrips(list) {
        const cur = trips.all();
        const ids = new Set(cur.map(t => t.id));
        let added = 0;
        list.forEach(t => { if (t && t.id && !ids.has(t.id)) { cur.push(t); ids.add(t.id); added++; } });
        trips.saveAll(cur);
        return added;
      },
      between: (from, to) => trips.all().filter(t => t.time >= from && t.time < to).sort((a, b) => a.time - b.time),
    };

    // ---- Ture ----
    const shifts = {
      current: () => load(K_SHIFTS, {}).current || null,
      all: () => load(K_SHIFTS, {}).list || [],
      saveAll(list) { const s = load(K_SHIFTS, {}); s.list = list; save(K_SHIFTS, s); },
      start(now) {
        const s = load(K_SHIFTS, {});
        if (!s.current) { s.current = now || Date.now(); save(K_SHIFTS, s); }
      },
      stop(now) {
        now = now || Date.now();
        const s = load(K_SHIFTS, {});
        if (!s.current) return;
        s.list = s.list || [];
        if (now - s.current >= 60000) s.list.push({ s: s.current, e: Math.min(now, s.current + MAX_SHIFT_MS) });
        delete s.current;
        save(K_SHIFTS, s);
      },
      withRunning(now) {
        now = now || Date.now();
        const cur = shifts.current();
        return cur ? shifts.all().concat([{ s: cur, e: Math.min(now, cur + MAX_SHIFT_MS) }]) : shifts.all();
      },
      deleteStartingIn(from, to) {
        shifts.saveAll(shifts.all().filter(x => !(x.s >= from && x.s < to)));
        const cur = shifts.current();
        if (cur && cur >= from && cur < to) { const s = load(K_SHIFTS, {}); delete s.current; save(K_SHIFTS, s); }
      },
    };

    // ---- Backup ----
    function backupJson(settings) {
      return JSON.stringify({
        app: 'merita', version: 1, created: Date.now(),
        trips: trips.all(), shifts: shifts.all(), settings,
      }, null, 1);
    }
    /** Întoarce {trips, newTrips, shifts, settings} — setările le pune pagina. */
    function restore(text) {
      const o = JSON.parse(text);
      if (!o || o.app !== 'merita') throw new Error('Fișierul nu e un backup Merită');
      const list = Array.isArray(o.trips) ? o.trips : [];
      const newTrips = trips.importTrips(list);
      const sh = Array.isArray(o.shifts) ? o.shifts : [];
      const seen = new Set(shifts.all().map(x => x.s));
      shifts.saveAll(shifts.all().concat(sh.filter(x => x && x.s && x.e && !seen.has(x.s))));
      return { trips: list.length, newTrips, shifts: sh.length, settings: o.settings || null };
    }

    return { trips, shifts, backupJson, restore };
  }

  // ---- Calcule fără stocare ----
  function round(v, d) { const f = Math.pow(10, d); return Math.round(v * f) / f; }

  /** Totalurile se fac doar din cursele luate. */
  function totals(list) {
    const acc = list.filter(t => t.status === ACCEPTED);
    const sum = k => acc.reduce((s, t) => s + (+t[k] || 0), 0);
    const r = {
      count: acc.length, offers: list.length, earnings: sum('earnings'), commission: sum('commission'),
      cost: sum('cost'), net: sum('net'), km: sum('km'), minutes: sum('minutes'),
    };
    r.expenses = r.commission + r.cost;
    r.perHour = r.minutes > 0 ? r.net / (r.minutes / 60) : 0;
    return r;
  }

  /** Minutele de tură care cad în intervalul [from, to). */
  function minutesIn(list, from, to) {
    let ms = 0;
    list.forEach(x => { const a = Math.max(x.s, from), b = Math.min(x.e, to); if (b > a) ms += b - a; });
    return Math.floor(ms / 60000);
  }
  function dayRange(y, m, d) { return [new Date(y, m, d).getTime(), new Date(y, m, d + 1).getTime()]; }
  function monthRange(y, m) { return [new Date(y, m, 1).getTime(), new Date(y, m + 1, 1).getTime()]; }

  const f0 = v => v.toFixed(0), f2 = v => v.toFixed(2);
  function pad(s, n) { s = String(s); return s.length >= n ? s : s + ' '.repeat(n - s.length); }
  function padL(s, n) { s = String(s); return s.length >= n ? s : ' '.repeat(n - s.length) + s; }
  function group(list) {
    const g = { count: list.length, net: 0, minutes: 0, km: 0 };
    list.forEach(t => { g.net += +t.net || 0; g.minutes += +t.minutes || 0; g.km += +t.km || 0; });
    g.perHour = g.minutes > 0 ? g.net / (g.minutes / 60) : 0;
    g.perKm = g.km > 0 ? g.net / g.km : 0;
    return g;
  }
  function line(label, g) {
    return pad(label, 9) + ' ' + padL(g.count, 3) + ' curse · net ' + padL(f0(g.net), 5) + ' · ' +
      padL(f0(g.perHour), 3) + ' RON/h · ' + f2(g.perKm) + ' RON/km';
  }
  function groupBy(list, fn) {
    const m = new Map();
    list.forEach(t => { const k = fn(t); if (!m.has(k)) m.set(k, []); m.get(k).push(t); });
    return m;
  }

  /** Raport text, la fel ca butonul „Statistici” de pe Android. */
  function report(title, list, shiftMinutes) {
    const acc = list.filter(t => t.status === ACCEPTED);
    if (!acc.length) return title + '\n\nNicio cursă luată în perioada asta.';
    const all = group(acc);
    let s = title + '\n\n';
    s += 'Curse luate: ' + acc.length + ' din ' + list.length + ' oferte văzute\n';
    s += 'Profit net: ' + f2(all.net) + ' RON · ' + f2(all.perKm) + ' RON/km\n';
    s += 'Pe timpul curselor: ' + f0(all.perHour) + ' RON/oră\n';
    if (shiftMinutes > 0) {
      s += 'Pe tură, cu așteptarea: ' + f0(all.net / (shiftMinutes / 60)) + ' RON/oră' +
        ' (' + Math.floor(shiftMinutes / 60) + ' h ' + (shiftMinutes % 60) + ' min de tură)\n';
    }

    s += '\nPE APLICAȚII\n';
    [...groupBy(acc, t => t.platform || '?')]
      .map(([k, v]) => [k, group(v)])
      .sort((a, b) => b[1].perHour - a[1].perHour)
      .forEach(([k, g]) => {
        const seen = list.filter(t => (t.platform || '?') === k).length;
        s += line(k.slice(0, 9), g) + ' · luate ' + g.count + '/' + seen + '\n';
      });

    s += '\nPE ZILE\n';
    const byDay = groupBy(acc, t => (new Date(t.time).getDay() + 6) % 7);
    for (let d = 0; d < 7; d++) if (byDay.has(d)) s += line(WEEKDAYS[d], group(byDay.get(d))) + '\n';

    s += '\nPE ORE\n';
    const byHour = groupBy(acc, t => Math.floor(new Date(t.time).getHours() / 2) * 2);
    const two = n => String(n).padStart(2, '0');
    [...byHour.keys()].sort((a, b) => a - b).forEach(h => {
      s += line(two(h) + '–' + two(h + 2), group(byHour.get(h))) + '\n';
    });
    let best = null;
    byHour.forEach((v, h) => { if (v.length >= 2 && (!best || group(v).perHour > best.g.perHour)) best = { h, g: group(v) }; });
    if (best) s += '→ Cele mai bune ore: ' + two(best.h) + '–' + two(best.h + 2) + ' (' + f0(best.g.perHour) + ' RON/h)\n';

    const top = acc.reduce((a, b) => (b.net > a.net ? b : a));
    const low = acc.reduce((a, b) => (b.net < a.net ? b : a));
    s += '\nCea mai bună cursă: ' + top.platform + ' net ' + f2(top.net) + ' RON (' + f2(top.earnings) + ' RON, ' + f2(top.km) + ' km)\n';
    s += 'Cea mai slabă cursă: ' + low.platform + ' net ' + f2(low.net) + ' RON (' + f2(low.earnings) + ' RON, ' + f2(low.km) + ' km)';
    return s;
  }

  /** Tabel pentru Excel: „;” și virgulă zecimală (Excel în română), BOM pentru diacritice. */
  function csv(list) {
    const n = v => (+v || 0).toFixed(2).replace('.', ',');
    const two = x => String(x).padStart(2, '0');
    const stare = { accepted: 'luată', rejected: 'refuzată' };
    const verdict = { GOOD: 'merită', MEH: 'la limită', BAD: 'nu merită' };
    let s = '﻿Data;Ora;Aplicație;Stare;Câștig RON;Km;Minute;Comision RON;Cost drum RON;Profit net RON;Verdict\r\n';
    list.slice().sort((a, b) => a.time - b.time).forEach(t => {
      const d = new Date(t.time);
      s += d.getFullYear() + '-' + two(d.getMonth() + 1) + '-' + two(d.getDate()) + ';' +
        two(d.getHours()) + ':' + two(d.getMinutes()) + ';' +
        String(t.platform || '').replace(/;/g, ',') + ';' + (stare[t.status] || 'doar văzută') + ';' +
        n(t.earnings) + ';' + n(t.km) + ';' + (t.minutes || 0) + ';' + n(t.commission) + ';' +
        n(t.cost) + ';' + n(t.net) + ';' + (verdict[t.verdict] || t.verdict || '') + '\r\n';
    });
    return s;
  }

  function fileName(prefix, ext) {
    const d = new Date(), two = x => String(x).padStart(2, '0');
    return prefix + '-' + d.getFullYear() + '-' + two(d.getMonth() + 1) + '-' + two(d.getDate()) + '.' + ext;
  }

  global.Journal = {
    create, totals, minutesIn, dayRange, monthRange, report, csv, fileName,
    OFFER, ACCEPTED, REJECTED, MAX_SHIFT_MS,
  };
})(typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : self));
