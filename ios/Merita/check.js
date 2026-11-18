// check.js — rulat de aplicația iPhone (JavaScriptCore) când Scurtătura trimite textul ofertei.
// Folosește același core.js ca pagina web și Android, ca verdictul să fie identic.
// Întoarce JSON: { ok, title, body, key, trip } — trip are formatul din journal.js.

function meritaCheck(text, settingsJson, now) {
  var saved = {};
  try { saved = JSON.parse(settingsJson || '{}') || {}; } catch (e) { saved = {}; }
  var s = Object.assign({}, Core.DEFAULT_SETTINGS, saved);
  var o = Core.parseOrder(String(text || ''));
  var hasTime = Number(o.minutes) > 0;

  if (o.earnings == null || (o.km == null && !hasTime)) {
    var miss = [];
    if (o.earnings == null) miss.push('câștigul');
    if (o.km == null && !hasTime) miss.push('km sau minutele');
    return JSON.stringify({
      ok: false,
      title: '❓ Nu găsesc ' + miss.join(' și '),
      body: 'Fă captura cât timp oferta e pe ecran.',
    });
  }

  var r = Core.calculate(o, s);
  var f = function (v) { return (Number(v) || 0).toFixed(2).replace('.', ','); };
  var head = { good: 'MERITĂ ✅', meh: 'LA LIMITĂ ⚠️', bad: 'NU MERITĂ ❌' }[r.verdict];
  var diff = o.earnings - r.minEarnings;
  var body =
    'net ' + f(r.net) + ' RON · ' + f(r.perKm) + ' RON/km · ' + Math.round(r.perHour || 0) + ' RON/h\n' +
    f(o.earnings) + ' RON · ' + (r.estimatedKm ? '~' + f(r.kmTotal) + ' km (est.)' : f(r.kmTotal) + ' km') +
    ' · ~' + r.minutes + ' min' + (r.estimatedTime ? ' (est.)' : '') + '\n' +
    'minim ca să merite: ' + f(r.minEarnings) + ' RON (' + (diff >= 0 ? '+' + f(diff) + ' peste' : f(-diff) + ' lipsă') + ')';

  var trip = {
    id: now, time: now, platform: o.platform || '', earnings: o.earnings,
    km: r.kmTotal, minutes: r.minutes, commission: Math.round((o.earnings - r.gross) * 100) / 100,
    cost: r.cost, net: r.net, verdict: String(r.verdict).toUpperCase(), status: 'offer',
  };
  return JSON.stringify({
    ok: true,
    title: head + (o.platform ? ' · ' + o.platform : ''),
    body: body,
    key: o.earnings + '|' + o.km + '|' + o.minutes,
    trip: trip,
  });
}
