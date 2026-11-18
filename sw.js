// Service worker: merge offline + primeste screenshot-uri prin "Distribuie" pe Android.
const APP = 'merita-v3';
const FILES = ['./', 'index.html', 'core.js', 'journal.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(APP).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== APP && k !== 'shared').map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  if (e.request.method === 'POST' && url.pathname.endsWith('/share')) {
    e.respondWith((async () => {
      const form = await e.request.formData();
      const cache = await caches.open('shared');
      const file = form.get('image');
      const text = [form.get('title'), form.get('text')].filter(Boolean).join('\n');
      if (file && file.size) await cache.put('shared-image', new Response(file));
      else if (text) await cache.put('shared-text', new Response(text));
      return Response.redirect('./?shared=1', 303);
    })());
    return;
  }

  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // Retea intai (ca sa iei mereu ultima versiune), cache daca nu e internet
  e.respondWith(
    fetch(e.request)
      .then(r => { const copy = r.clone(); caches.open(APP).then(c => c.put(e.request, copy)); return r; })
      .catch(() => caches.match(e.request, { ignoreSearch: true }))
  );
});
