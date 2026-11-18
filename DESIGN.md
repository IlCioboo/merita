# Design — „Merită cursa?” (iPhone + web)

Pagina `index.html` e aceeași în aplicația de iPhone (WKWebView) și pe web. Android are ecranele lui, native (Kotlin), în alt stil.

## Lumea

Întunecat, ca o aplicație de iPhone folosită noaptea în mașină: negru, grupuri gri închis, text alb.
O singură culoare pentru butoane și selecție (albastru iOS). Verde / galben / roșu apar doar unde au sens:
verdictul, bara de prag, starea curselor, tura pornită.

## Tokeni (`:root` în index.html)

| Rol | Valoare |
|---|---|
| fundal / grup / grup 2 / grup 3 | `#000` / `#1c1c1e` / `#2c2c2e` / `#3a3a3c` |
| text / text secundar / placeholder | `#fff` / `rgba(235,235,245,.64)` / `.5` |
| separator | `rgba(84,84,88,.6)` |
| acțiune (tint) | `#0a84ff` |
| merită / la limită / nu merită | `#30d158` / `#ffd60a` / `#ff453a`, text negru pe ele |

Font: fontul sistemului; pe iPhone `-apple-system-body`, deci textul urmează mărimea aleasă în Configurări.
Mărimile sunt în `rem` (1rem = 17 px): titlu 2rem, valori 1.294–1.647rem, note .765rem. Cifrele sunt tabulare.

## Structură

- Bară de tab-uri jos (Ofertă, Calendar, Statistici, Setări), cu iconițe SVG desenate în pagină (`<symbol>`); tab-ul rămâne ținut minte.
- Ofertă: cardul „Azi” (profit net azi, curse, tura) → cardul verdictului → Am luat-o / Am refuzat-o → câmpurile comenzii ca listă grupată → Captură / Lipește.
- Calendar: luna, ziua aleasă (sumar, curse cu starea ca buton „Luată / Văzută / Refuzată”), sumarul lunii.
- Statistici: aceleași cifre ca `Journal.report` (Android), așezate ca liste: total, pe aplicații, pe zile, pe ore, extreme.
- Setări: liste grupate ca în Configurări, apoi Backup și export.

## Semnătura

Cardul verdictului: banda colorată cu verdictul și, dedesubt, **bara de prag**: cât e oferta față de minimul
cu care merită (linia albă). Se citește dintr-o privire, fără cifre.

## Mișcare

Doar stare: verdictul își schimbă culoarea (200 ms), bara de prag alunecă, punctul roșu pulsează cât merge tura,
tab-urile apar cu un fade scurt. Toate se opresc cu „Reduce mișcarea”.
