# Dongle Favicon + Header-Logo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Favicon-404-Spam im ESP32-Log eliminieren und das Answerbot-Logo als visuelles Branding-Element in die Dongle-Config-Page einbauen. Gleichzeitig das Google-Blau durch das zum Logo passende Answerbot-Türkis ersetzen.

**Architecture:** Eine einzige minifizierte SVG-Datei (~2 KB) wird per `EMBED_FILES` ins Firmware-Binary eingebettet und unter zwei URL-Pfaden (`/favicon.ico` und `/ab-logo-bot.svg`) über denselben Handler ausgeliefert. `index.html` referenziert beide Pfade — `<link rel="icon">` im Head und `<img>` im Header — und wechselt seine Primärfarbe von `#1a73e8` auf `#00d1b2` / `#008f7a`.

**Tech Stack:** ESP-IDF v5.3, `esp_http_server`, C, HTML/CSS, SVG.

**Spec:** [docs/superpowers/specs/2026-04-19-dongle-favicon-logo-design.md](../specs/2026-04-19-dongle-favicon-logo-design.md)

---

## File Structure

**Neu erstellt:**
- `phoneblock-dongle/firmware/main/web/ab-logo-bot.svg` — minifiziertes Answerbot-Logo, wird als Favicon und als Header-Bild ausgeliefert.

**Modifiziert:**
- `phoneblock-dongle/firmware/main/CMakeLists.txt` — SVG zu `EMBED_FILES` hinzufügen.
- `phoneblock-dongle/firmware/main/web.c` — Extern-Symbole für das SVG, `handle_favicon`-Funktion, zwei neue Einträge in `URIS[]`.
- `phoneblock-dongle/firmware/main/web/index.html` — Favicon-Link, Header-Img, CSS-Farbanpassungen.

Keine neuen Komponenten, keine NVS-Einträge, keine API-Änderungen.

**Testing-Modell:** Die Dongle-Firmware hat nur einen Unit-Test (`test_sip_parse.c`) für reine Parser-Logik. Web-Handler werden klassisch manuell getestet (Build + Flash + Browser). Jede Task endet deshalb entweder mit einem `idf.py build` als Compile-Check oder mit einem Browser-Durchlauf.

---

## Task 1: Minifiziertes Answerbot-Logo als SVG anlegen

**Files:**
- Create: `phoneblock-dongle/firmware/main/web/ab-logo-bot.svg`

**Ziel:** Eine SVG-Datei, die exakt das Answerbot-Logo darstellt, ohne Inkscape-Metadaten, ohne unbenutzte `<defs>`, mit zusammengefasstem äußeren `<g transform="">`. Quelle: `phoneblock/src/main/webapp/assets/img/ab/logo/ab-logo-bot.svg`.

Die beiden nested `<g>`-Wrapper der Quelle haben Translate-Werte `(-48.3982, -17.533533)` und `(-40.083224, 0)`. Summiert ergibt das den einen Wrapper-Translate `(-88.481424, -17.533533)` in der minifizierten Version.

- [ ] **Step 1: Datei mit folgendem Inhalt anlegen**

`phoneblock-dongle/firmware/main/web/ab-logo-bot.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 135.46667 135.46666"><g transform="translate(-88.481424,-17.533533)"><path fill="#00d1b2" d="m 156.21478,17.533534 c -33.42803,1.36e-4 -60.526765,27.587367 -60.526596,61.617808 0.01016,12.683584 3.863795,25.055138 11.036526,35.425978 3.61637,12.49979 1.52076,25.30386 -6.4965,38.42288 7.43948,-0.61624 15.50982,-1.95371 34.75775,-16.07222 6.81003,2.6181 13.94936,3.83189 21.22882,3.84115 33.42808,-9e-5 60.52677,-27.58733 60.52655,-61.617788 2.2e-4,-34.030457 -27.09847,-61.617725 -60.52655,-61.617808 z"/><path fill="#fff" d="m 183.07479,102.33493 c 2.12213,0 3.83065,1.70852 3.83065,3.83064 v 13.30828 c 0,2.12212 -1.70852,3.82977 -3.83065,3.82977 -2.12212,0 -3.83059,-1.70765 -3.83065,-3.82977 v -13.30828 c 6e-5,-2.12212 1.70853,-3.83064 3.83065,-3.83064 z"/><path fill="#fff" d="m 129.35473,102.33493 c 2.12213,0 3.83066,1.70852 3.83066,3.83064 v 13.30828 c 0,2.12212 -1.70853,3.82977 -3.83066,3.82977 -2.12212,0 -3.83062,-1.70765 -3.83062,-3.82977 v -13.30828 c 0,-2.12212 1.7085,-3.83064 3.83062,-3.83064 z"/><path fill="#fff" d="m 134.83185,102.25341 h 3.88231 35.08443 3.88231 v 4.17045 18.20649 2.29015 c 0,2.31031 -1.73158,4.16955 -3.88231,4.16955 h -35.08443 c -2.15073,0 -3.88231,-1.85924 -3.88231,-4.16955 v -2.29015 -18.20649 z"/><path fill="#fff" d="m 155.96341,23.709515 a 5.4850878,5.4855783 0 0 1 5.48502,5.484972 5.4850878,5.4855783 0 0 1 -3.80438,5.219628 v 6.091888 c 0,0.212281 -0.0349,0.415921 -0.0988,0.604291 h 6.00081 c 0.8031,0 1.47831,0.528889 1.69811,1.258487 17.19755,0.662301 30.81416,12.875735 30.81416,27.948617 v 2.522232 c 0,19.682458 -9.53639,27.97927 -32.28549,27.97927 h -15.11586 c -20.00437,0 -32.28544,-5.543657 -32.28544,-27.97927 v -2.522232 c 0,-14.997721 13.48067,-27.165551 30.55664,-27.93899 0.21676,-0.734548 0.89321,-1.268114 1.69993,-1.268114 h 5.76696 c -0.0641,-0.18837 -0.0988,-0.39201 -0.0988,-0.604291 v -6.086635 a 5.4850878,5.4855783 0 0 1 -3.81752,-5.224881 5.4850878,5.4855783 0 0 1 5.48498,-5.484972 z"/><path fill="#00d1b2" d="m 147.09882,87.257516 c -1.02468,3.3e-5 -1.85522,0.830994 -1.85486,1.855762 0,0.689263 0.44566,1.223663 0.99401,1.641209 5.29011,4.02837 10.87214,6.101854 19.86867,0.03932 0.58288,-0.39277 1.04167,-0.939168 1.04217,-1.642072 3.6e-4,-1.0035 -0.81295,-1.817189 -1.81632,-1.817241 -0.62458,6.27e-4 -1.06872,0.437934 -1.53703,0.851261 -5.0223,4.432428 -10.38132,3.559998 -12.73723,1.635939 -2.55798,-2.130138 -3.22377,-2.564378 -3.95941,-2.56426 z"/><path fill="#404040" d="m 138.54601,60.110234 c -8.2987,0 -7.99616,1.346378 -14.97927,11.318517 -4.65698,6.650369 6.33131,11.318515 14.97927,11.318515 h 14.38463 c 0.70303,-1.857168 1.58464,-4.686263 3.25962,-4.629337 1.48869,0.247635 2.42319,2.726108 3.15891,4.629337 h 15.21046 c 8.33338,0 18.69965,-6.150626 14.97925,-11.318515 -6.77335,-9.408575 -6.68048,-11.318517 -14.97925,-11.318517 z"/><path fill="#fff" d="m 169.89879,65.664403 a 5.4416469,5.4421336 0 0 1 5.44115,5.442061 5.4416469,5.4421336 0 0 1 -5.44115,5.442062 5.4416469,5.4421336 0 0 1 -5.44211,-5.442062 5.4416469,5.4421336 0 0 1 5.44211,-5.442061 z"/><path fill="#fff" d="m 142.30219,65.664403 a 5.4416469,5.4421336 0 0 1 5.44123,5.442061 5.4416469,5.4421336 0 0 1 -5.44123,5.442062 5.4416469,5.4421336 0 0 1 -5.44206,-5.442062 5.4416469,5.4421336 0 0 1 5.44206,-5.442061 z"/></g></svg>
```

- [ ] **Step 2: Visuelle Korrektheit im Browser prüfen**

Die Datei in einem beliebigen Browser direkt öffnen (`xdg-open firmware/main/web/ab-logo-bot.svg`) und bestätigen:

- Türkise Chat-Bubble (`#00d1b2`)
- Weißer Roboter mit dunkler Augenmaske (`#404040`)
- Lächelnder Mund
- Keine Warnungen in der Browser-Konsole

Expected: Das Logo sieht identisch aus zur Quelldatei `phoneblock/src/main/webapp/assets/img/ab/logo/ab-logo-bot.svg`.

- [ ] **Step 3: Dateigröße prüfen**

Run:
```bash
wc -c phoneblock-dongle/firmware/main/web/ab-logo-bot.svg
```
Expected: Zwischen 1800 und 2500 Bytes.

- [ ] **Step 4: Commit**

```bash
git add phoneblock-dongle/firmware/main/web/ab-logo-bot.svg
git commit -m "$(cat <<'EOF'
feat(dongle): Add minified Answerbot logo SVG for web UI

Minified copy of phoneblock/src/main/webapp/assets/img/ab/logo/ab-logo-bot.svg
for use as favicon and header graphic in the Dongle config page.
Inkscape metadata stripped, unused marker defs removed, nested
transforms merged — ~2 KB embedded flash footprint.
EOF
)"
```

---

## Task 2: Firmware — SVG einbetten und unter `/favicon.ico` + `/ab-logo-bot.svg` ausliefern

**Files:**
- Modify: `phoneblock-dongle/firmware/main/CMakeLists.txt`
- Modify: `phoneblock-dongle/firmware/main/web.c`

**Ziel:** Das in Task 1 angelegte SVG wird ins Binary eingebettet, unter zwei URLs mit `Content-Type: image/svg+xml` ausgeliefert und langzeit-gecached. Eine kurze manuelle Firmware-Startprüfung bestätigt, dass keine neuen Fehler beim Linken oder beim HTTP-Server-Start entstehen.

- [ ] **Step 1: `CMakeLists.txt` erweitern**

Datei: `phoneblock-dongle/firmware/main/CMakeLists.txt`

Die Zeile
```cmake
    EMBED_FILES "audio/announcement.alaw" "web/index.html"
```
ersetzen durch:
```cmake
    EMBED_FILES "audio/announcement.alaw" "web/index.html" "web/ab-logo-bot.svg"
```

- [ ] **Step 2: Extern-Symbole in `web.c` deklarieren**

Datei: `phoneblock-dongle/firmware/main/web.c`, direkt nach den bereits existierenden `index_html_start`/`index_html_end` (ca. Zeilen 51–52) einfügen:

```c
extern const uint8_t ab_logo_bot_svg_start[] asm("_binary_ab_logo_bot_svg_start");
extern const uint8_t ab_logo_bot_svg_end[]   asm("_binary_ab_logo_bot_svg_end");
```

- [ ] **Step 3: Handler-Funktion implementieren**

Datei: `phoneblock-dongle/firmware/main/web.c`, direkt vor `handle_root` (ca. Zeile 91) einfügen:

```c
static esp_err_t handle_favicon(httpd_req_t *req)
{
    httpd_resp_set_type(req, "image/svg+xml");
    httpd_resp_set_hdr(req, "Cache-Control", "public, max-age=31536000, immutable");
    return httpd_resp_send(req, (const char *)ab_logo_bot_svg_start,
                           ab_logo_bot_svg_end - ab_logo_bot_svg_start);
}
```

- [ ] **Step 4: URIs in `URIS[]` registrieren**

Datei: `phoneblock-dongle/firmware/main/web.c`, in der `URIS[]`-Tabelle (ca. Zeilen 684–694). Die Zeile
```c
    { .uri = "/",            .method = HTTP_GET,  .handler = handle_root,        .user_ctx = NULL },
```
ersetzen durch:
```c
    { .uri = "/",                .method = HTTP_GET,  .handler = handle_root,        .user_ctx = NULL },
    { .uri = "/favicon.ico",     .method = HTTP_GET,  .handler = handle_favicon,     .user_ctx = NULL },
    { .uri = "/ab-logo-bot.svg", .method = HTTP_GET,  .handler = handle_favicon,     .user_ctx = NULL },
```

`cfg.max_uri_handlers` muss **nicht** manuell angepasst werden — Zeile 704 (`cfg.max_uri_handlers = sizeof(URIS) / sizeof(URIS[0]) + 2;`) berechnet sich automatisch aus der neuen Tabellengröße.

- [ ] **Step 5: Firmware-Build verifizieren**

Run:
```bash
cd phoneblock-dongle/firmware
idf.py build
```
Expected: Build läuft erfolgreich durch, am Ende steht `Project build complete.`. Insbesondere keine Linker-Fehler à la `undefined reference to ab_logo_bot_svg_start`.

Falls der Build fehlschlägt: Prüfen, dass der Dateiname in `CMakeLists.txt` exakt zu `web/ab-logo-bot.svg` passt und die Symbolnamen in Step 2 das Format `_binary_<file>_<ext>_{start,end}` mit Unterstrichen statt Punkten befolgen.

- [ ] **Step 6: Commit**

```bash
git add phoneblock-dongle/firmware/main/CMakeLists.txt phoneblock-dongle/firmware/main/web.c
git commit -m "$(cat <<'EOF'
feat(dongle): Serve Answerbot logo as favicon and standalone SVG

Embed web/ab-logo-bot.svg into the firmware image and register two
URL handlers that stream it with image/svg+xml and a one-year
immutable Cache-Control header:

- /favicon.ico     — silences browsers' implicit probe and the
                     matching httpd 404 log spam.
- /ab-logo-bot.svg — referenced from index.html's <link rel=icon>
                     and the header <img>.
EOF
)"
```

---

## Task 3: Config-Page — Favicon, Header-Logo und Farbpalette umstellen

**Files:**
- Modify: `phoneblock-dongle/firmware/main/web/index.html`

**Ziel:** `index.html` verlinkt das Favicon, zeigt das Logo im Header neben dem Titel und verwendet durchgängig Answerbot-Türkis statt Google-Blau. Keine Struktur- oder Inhaltsänderungen außerhalb dieser drei Punkte.

- [ ] **Step 1: Favicon-Link in `<head>` einfügen**

Datei: `phoneblock-dongle/firmware/main/web/index.html`, direkt vor `<title>PhoneBlock Dongle</title>` (Zeile 6):

```html
<link rel="icon" type="image/svg+xml" href="/ab-logo-bot.svg">
```

Der `<head>`-Anfang sieht danach so aus:

```html
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="icon" type="image/svg+xml" href="/ab-logo-bot.svg">
<title>PhoneBlock Dongle</title>
```

- [ ] **Step 2: Header-CSS auf Flex + Türkis umstellen**

Datei: `phoneblock-dongle/firmware/main/web/index.html`, Zeile 11:

Alt:
```css
  header{background:#1a73e8;color:#fff;padding:.8rem 1rem}
```

Neu (zwei CSS-Regeln):
```css
  header{background:#00d1b2;color:#fff;padding:.8rem 1rem;
         display:flex;align-items:center;gap:.6rem}
  header img{display:block;width:32px;height:32px}
```

- [ ] **Step 3: Stat-Value-Farbe auf dunkles Türkis umstellen**

Datei: `phoneblock-dongle/firmware/main/web/index.html`, Zeile 26:

Alt:
```css
  .stat-value{font-size:1.6rem;font-weight:600;color:#1a73e8;line-height:1.2}
```

Neu:
```css
  .stat-value{font-size:1.6rem;font-weight:600;color:#008f7a;line-height:1.2}
```

- [ ] **Step 4: Primary-Button-Hintergrund auf Türkis umstellen**

Datei: `phoneblock-dongle/firmware/main/web/index.html`, Zeilen 40–41:

Alt:
```css
  .btn-primary{background:#1a73e8;color:#fff;border:0;padding:.55rem 1.2rem;
               border-radius:4px;font-size:1rem;cursor:pointer;font-family:inherit}
```

Neu:
```css
  .btn-primary{background:#00d1b2;color:#fff;border:0;padding:.55rem 1.2rem;
               border-radius:4px;font-size:1rem;cursor:pointer;font-family:inherit}
```

- [ ] **Step 5: `<header>`-Markup um Logo ergänzen**

Datei: `phoneblock-dongle/firmware/main/web/index.html`, Zeile 48:

Alt:
```html
<header><h1>PhoneBlock Dongle</h1></header>
```

Neu:
```html
<header>
  <img src="/ab-logo-bot.svg" alt="" width="32" height="32">
  <h1>PhoneBlock Dongle</h1>
</header>
```

Das `alt=""` ist beabsichtigt: Das Logo ist dekorativ, der darauf folgende `<h1>` enthält bereits den Text-Äquivalentwert — Screenreader überspringen das Bild dann korrekt.

- [ ] **Step 6: Nach weiteren `#1a73e8`-Vorkommen suchen**

Run:
```bash
grep -n "1a73e8" phoneblock-dongle/firmware/main/web/index.html
```
Expected: Keine Ausgabe. Falls noch Treffer kommen, auf `#00d1b2` (Hintergründe) bzw. `#008f7a` (Text auf weiß) aktualisieren.

- [ ] **Step 7: Firmware-Build verifizieren**

Run:
```bash
cd phoneblock-dongle/firmware
idf.py build
```
Expected: `Project build complete.`.

- [ ] **Step 8: Commit**

```bash
git add phoneblock-dongle/firmware/main/web/index.html
git commit -m "$(cat <<'EOF'
feat(dongle): Add favicon link, header logo and Answerbot colors

Link the embedded SVG as favicon, show the logo next to the title
in the config page header, and swap the previous Google-blue
accent (#1a73e8) for the Answerbot turquoise palette:

- #00d1b2 for the header background and primary-button fill
- #008f7a for .stat-value text on white (WCAG-AA on white)
EOF
)"
```

---

## Task 4: Ende-zu-Ende-Prüfung auf dem Dongle

**Files:** keine Änderungen — nur Verifikation.

**Ziel:** Auf echter Hardware bestätigen, dass (a) das 404-Log zum Favicon verschwunden ist, (b) das Logo im Tab und im Header sichtbar ist, (c) die Farben tatsächlich türkis sind.

- [ ] **Step 1: Auf Dongle flashen und Log-Monitor starten**

Run (Port ggf. anpassen):
```bash
cd phoneblock-dongle/firmware
idf.py -p /dev/ttyUSB0 flash monitor
```
Expected: Build+Flash+Monitor laufen durch, Firmware bootet bis zur Zeile `HTTP server listening on :80`.

- [ ] **Step 2: Config-Page im Browser öffnen**

Die IP-Adresse aus dem Monitor-Log holen (WiFi- oder Ethernet-Log-Zeile) und `http://<ip>/` in einem aktuellen Chrome/Firefox/Safari aufrufen.

Expected:
- Browser-Tab zeigt das türkise Roboter-Logo als Favicon
- Header-Zeile zeigt das 32×32-Logo links neben „PhoneBlock Dongle"
- Header-Hintergrund ist türkis (`#00d1b2`), nicht blau
- Stat-Zahlen (Gesamt/Spam/Legitim/Fehler) sind dunkel-türkis (`#008f7a`)
- „Fritz!Box-Einrichtung"-Button ist türkis

- [ ] **Step 3: Favicon-Request im DevTools-Network-Tab prüfen**

In den Browser-DevTools → Network → Reload:

Expected:
- `/ab-logo-bot.svg` → `200` mit `Content-Type: image/svg+xml` und `Cache-Control: public, max-age=31536000, immutable`
- Entweder kein `/favicon.ico`-Request (falls Browser den Link-Tag nutzt) **oder** `/favicon.ico` → `200` mit `image/svg+xml`. Beides ist OK.

- [ ] **Step 4: Monitor-Log auf 404-Spam prüfen**

Im laufenden `idf.py monitor` nach dem Seitenaufruf sicherstellen:

Expected: Keine neuen Zeilen der Form `httpd_uri: URI '/favicon.ico' not found` oder `httpd_resp_send_err: 404 Not Found`.

Falls doch: `/favicon.ico`-Registrierung in `URIS[]` prüfen (Task 2, Step 4).

- [ ] **Step 5: Push des fertigen Features**

```bash
git push
```
Expected: Die drei Commits (SVG-Asset, Firmware-Handler, UI-Redesign) landen auf dem Remote `phoneblock-dongle`-Branch.
