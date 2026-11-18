# „Merită?” pe iPhone — instalare gratuită

Aplicația se construiește pe un Mac de la GitHub (gratuit) și se pune pe iPhone de pe PC cu **Sideloadly**,
folosind Apple ID-ul tău. Gratuit înseamnă că **expiră la 7 zile** și o reinstalezi în 2 minute (cursele rămân).

---

## 1. GitHub (o singură dată)

1. Fă-ți cont gratuit pe **github.com** (Sign up).
2. Sus-dreapta **+ → New repository** → nume `merita` → **Private** → *Create repository* (fără README).
3. Trimite-mi linkul (ex. `https://github.com/numele-tau/merita`). Eu urc codul; prima dată Git îți
   deschide o fereastră în care te loghezi în GitHub.
4. GitHub construiește singur aplicația (5–10 minute): pe pagina repo-ului → **Actions** → **iPhone** →
   ultima rulare (bifă verde) → jos la **Artifacts** → **Merita-iPhone** → se descarcă un zip →
   îl dezarhivezi → **Merita.ipa**.

## 2. Pe PC (o singură dată)

1. Instalează **iTunes** și **iCloud** de pe **apple.com** (varianta de pe site, nu din Microsoft Store) —
   Sideloadly are nevoie de ele ca să vadă iPhone-ul prin cablu.
2. Instalează **Sideloadly** de pe **sideloadly.io**.

## 3. Instalarea pe iPhone

1. Leagă iPhone-ul cu cablul → pe iPhone apasă **Ai încredere** în acest computer.
2. În Sideloadly: trage **Merita.ipa** în fereastră, scrie Apple ID-ul (emailul) → **Start**.
   Îți cere parola Apple ID și codul de verificare (le scrii tu; merg doar la Apple).
3. Pe iPhone: **Configurări → General → VPN și gestionare dispozitiv** → Apple ID-ul tău → **Ai încredere**.
4. Pe iPhone: **Configurări → Confidențialitate și securitate → Mod dezvoltator** → **Activat** → repornește
   telefonul și confirmă.
5. Deschide **Merită?** → **Permite** notificările. Pune-ți vehiculul și pragurile în *Vehicul și praguri*.

## 4. Scurtătura (dublu-tap pe spatele telefonului)

1. Aplicația **Scurtături** → **+**.
2. Adaugă, în ordine:
   - **Fă o captură de ecran** (*Take Screenshot*)
   - **Extrage textul din imagine** (*Extract Text from Image*)
   - caută **Verifică oferta** (de la *Merită?*) → la *Text* alege **Text extras**
3. Numește scurtătura **Merită**.
4. **Configurări → Accesibilitate → Atingere → Atingere pe spate → Atingere dublă → Merită**.

**Cum o folosești:** vine oferta în Bolt/Glovo/Wolt → bați de două ori pe spatele telefonului →
sus apare notificarea: *MERITĂ ✅ · net … · minim ca să merite …*.
Trage notificarea în jos (sau ține apăsat pe ea) → **✓ Am luat-o** / **✖ Am refuzat-o**.
Cursa apare în calendar data viitoare când deschizi aplicația.

## 5. La fiecare 7 zile

Leagă iPhone-ul → Sideloadly → același **Merita.ipa** → **Start**. Cursele și setările rămân
(doar nu șterge aplicația de pe telefon). Dacă a expirat, aplicația nu se mai deschide până o reinstalezi —
datele sunt tot acolo.

Fă din când în când **💾 Salvează backup** (secțiunea *Backup și export*) în Fișiere / iCloud.

---

### Pentru cine modifică aplicația

- `ios/Merita/*.swift` — partea nativă: ecranul (WKWebView cu `index.html`), acțiunea pentru Scurtături
  (`CheckOfferIntent`), notificarea (`Notifier`), legătura cu pagina (`Store` + `MeritaNative.receive`).
- `ios/Merita/check.js` — verdictul pentru notificare, folosind `core.js` (testabil cu node).
- Pagina, calendarul, statisticile, backup-ul: aceleași fișiere ca varianta web (`index.html`, `core.js`, `journal.js`),
  copiate în aplicație la build (`.github/workflows/iphone.yml`).
- Proiectul Xcode se generează cu XcodeGen din `ios/project.yml`; build nesemnat, Sideloadly semnează.
