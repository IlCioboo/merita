# Merită cursa? — calculator de profit pentru Glovo / Wolt / Uber Eats

Citește comanda de pe ecran (câștig, km, minute), scade costul drumului după vehiculul tău și îți spune
**MERITĂ ✅ / LA LIMITĂ ⚠️ / NU MERITĂ ❌**, cu profit net, RON/km și RON/oră.

| Telefon | Ce folosești | Cum pornește |
|---|---|---|
| **iPhone** | `iphone/Merita.js` în aplicația gratuită **Scriptable** + o Scurtătură | dublu-tap pe spatele telefonului → notificare peste Glovo/Wolt/Uber |
| **Android** | `Merita.apk` | singur, fără să atingi nimic → bulă colorată peste aplicație |
| oricare | aplicația web (`index.html`) | alternativă; trebuie pusă online |

## Cum se calculează
- **Cost/km** = consum/100 × preț carburant (sau kWh) + uzură/km
- **Km total** = km comandă × (1 + km retur %)
- **Timp** = minutele din comandă, sau km ÷ viteza medie, + timpul de așteptare la restaurant
- **Profit net** = câștig × (1 − comision %) − km total × cost/km
- **Verdict**: verde dacă trece ambele praguri (RON/km și RON/oră), galben dacă trece unul, roșu dacă niciunul.
- Câștigul = cea mai mare sumă de pe ecran (fără „azi”, „sold”, „săptămână” și fără sume peste 300 RON).
  Distanța = suma tuturor „km/m”, sau valoarea marcată „total”.

---

## iPhone

Apple nu lasă nicio aplicație să citească singură ecranul altei aplicații. Așa că apeși tu o dată
(dublu-tap pe spate), iar restul e automat și nu ieși din Glovo.

### 1. Pune scriptul în Scriptable (o singură dată)
1. Instalează **Scriptable** din App Store (gratuită, dezvoltator Simon Støvring).
2. Trimite-ți fișierul `iphone/Merita.js` pe iPhone (mail, WhatsApp către tine, iCloud Drive).
3. Varianta A: deschide fișierul pe iPhone → **Distribuie** → **Salvează în Fișiere** → **iCloud Drive → Scriptable**.
   Varianta B: deschide `Merita.js`, copiază tot textul, apoi în Scriptable apasă **+**, lipește
   și redenumește scriptul **Merita** (atingi titlul de sus).
4. În Scriptable atinge **Merita** → se deschide meniul: alege **Vehicul și costuri**, apoi **Praguri și ajustări**.

### 2. Fă Scurtătura (o singură dată)
În aplicația **Scurtături** → **+** → adaugă, în ordine:
1. **Fă o captură de ecran** (*Take Screenshot*)
2. **Extrage textul din imagine** (*Extract Text from Image*)
3. **Rulează scriptul** (*Run Script*, din Scriptable) → alege **Merita** → la *Parametru* pune
   **Text din imagine**. Lasă „Rulează în aplicație” **oprit**.
4. **Afișează notificarea** (*Show Notification*) → în text pune **Rezultatul scriptului** (*Script Result*).

Denumește scurtătura **Merită?**.

### 3. Buton plutitor pe ecran (AssistiveTouch)
Un buton rotund care stă mereu deasupra oricărei aplicații, inclusiv peste Glovo/Wolt/Uber:
1. **Configurări → Accesibilitate → Atingere → AssistiveTouch** → pornește-l.
2. La **Acțiuni personalizate → O singură atingere** alege scurtătura **Merită?**.
3. Opțional: **Opacitate în repaus** pe ~40%, ca să nu te încurce. Butonul îl muți oriunde trăgându-l.

(Alternative: **Atingere pe spate → Atingere dublă** din același meniu, sau butonul de Acțiune pe iPhone 15 Pro și mai nou.)

### Folosire
Când apare comanda, **apeși butonul plutitor**. După 1–2 secunde apare o notificare sus:

```
MERITĂ ✅ · Uber Eats
net 14.94 RON · 2.87 RON/km · 31 RON/h
18.45 RON · 5.2 km · ~29 min · cost 3.51
```

Prima dată iOS te întreabă dacă Scurtătura are voie să ruleze Scriptable și să afișeze notificări: alegi **Permite întotdeauna**.

**Dacă apare „❓ Nu am găsit…”:** deschide Scriptable → Merita → **Ultimul text citit** → **Copiază**
și trimite-mi textul, ca să ajustez citirea. Din același meniu ai și **Istoric** și **Testează cu textul din clipboard**.

---

## Android — aplicația nativă (citește singură, fără screenshot)
Fișierul gata de instalat: **`Merita.apk`**. Codul e în folderul `android/`.

**Instalare:**
1. Copiază `Merita.apk` pe telefon (cablu, Drive, WhatsApp către tine) și deschide-l.
   Permite „instalare din surse necunoscute” când te întreabă.
2. Deschide aplicația **Merită?** → **1. Activează citirea automată** → în listă alege
   **Merită? – citire comenzi** → pornește.
   - Dacă e gri / „Setare restricționată” (Android 13+): **Setări → Aplicații → Merită? → ⋮ (dreapta sus)
     → Permite setările restricționate**, apoi încearcă din nou.
3. Completează vehiculul și pragurile. Apasă **Arată bula de test peste ecran** ca să vezi cum arată.

**Folosire:** nu faci nimic. Când apare o comandă nouă în Glovo, Wolt sau Uber Eats, apare sus o bulă
verde/galbenă/roșie cu profitul net, RON/km și RON/oră. Atingi bula ca s-o închizi, o tragi sus/jos
ca s-o muți. Dispare singură după 25 de secunde (se schimbă din setări).

**Buton plutitor de scanare:** în aplicație pornești **„Buton plutitor: apeși și scanează ecranul”**.
Apare un cerc verde „RON ?” deasupra oricărei aplicații. Îl apeși → citește ecranul pe loc și arată bula
(merge în orice aplicație, nu doar în cele urmărite). Îl tragi ca să-l muți.
Dacă vrei doar butonul, fără bule automate, oprește **„Citire automată”**.

**Calendar și câștiguri:** aplicația salvează singură fiecare ofertă care apare.
- O marchează **acceptată** automat când apeși „Acceptă” în Glovo, Wolt sau Uber, sau când aplicația trece la navigarea spre restaurant.
- Dacă n-a prins acceptarea (de ex. la glisare), apeși **✓ Am luat-o** pe bulă.
- În **📅 Calendar** vezi fiecare zi colorată după profit. Atingi o zi și vezi: încasat, cheltuieli (carburant + uzură + comision), profit, timp estimat, km, RON/oră și lista curselor.
- Atingi o cursă ca s-o marchezi luată / nu / s-o ștergi. Cu **+ Adaugă cursă manual** pui o cursă pe care aplicația n-a văzut-o.
- Doar cursele ✅ intră în totaluri. Costurile se calculează cu prețurile din momentul cursei.

**Dacă nu apare bula:**
- Verifică în aplicație să scrie „Citirea automată e PORNITĂ”.
- La „Aplicații urmărite” trebuie să fie aplicația ta de curier. Implicit sunt
  `com.glovoapp.courier`, `com.wolt.courierapp`, `com.ubercab.driver`. Dacă folosești altă aplicație,
  deschide-o o dată, revino în Merită? și atinge-o în lista „Deschise recent”.
- La „Ultimul text citit” vezi exact ce a citit de pe ecran. Apasă **Trimite textul citit** și trimite-mi-l
  ca să ajustez recunoașterea.
- Unele telefoane (Xiaomi, Huawei, Samsung) opresc serviciile în fundal: pune Merită? pe
  „Fără restricții” la baterie.

---

## Aplicația web (alternativă, oricare telefon)
`index.html` + `core.js` + `sw.js` + `manifest.webmanifest` + iconițe. Trebuie pusă pe o adresă `https://`
(gratuit pe Netlify: *Add new site → Deploy manually* → tragi folderul; sau GitHub Pages).
Pe Android o instalezi din Chrome (⋮ → Instalează aplicația) și îi trimiți screenshot-uri cu **Distribuie**.
Pe iPhone o Scurtătură poate deschide `ADRESA#t=` + textul extras din captură (codificat URL).

---

## Pentru modificări (pe PC)
- Logica de citire și calcul: `core.js` (web + iPhone) și `android/app/src/main/java/ro/merita/app/Core.kt` (Android).
- După ce modifici `core.js` sau `iphone/merita-main.js`, regenerează scriptul de iPhone:
  `powershell -ExecutionPolicy Bypass -File iphone\build.ps1`
- Recompilare Android (uneltele sunt în `C:\Users\Asus\.merita-build`):
  `$env:JAVA_HOME="C:\Users\Asus\.merita-build\jdk"; cd android; .\gradlew.bat assembleDebug`
- Test local al aplicației web: `serve.ps1` → http://localhost:8770
