# Dongle Web UI: Favicon und Answerbot-Logo im Header

**Datum:** 2026-04-19
**Scope:** `phoneblock-dongle/firmware/`

## Problem

Die aktuelle Dongle-Web-UI (`firmware/main/web/index.html`) hat kein Favicon.
Browser probieren beim ersten Laden `/favicon.ico` und erzeugen ESP32-Log-Spam:

```
W httpd_uri: URI '/favicon.ico' not found
W httpd_txrx: httpd_resp_send_err: 404 Not Found - Nothing matches the given URI
```

Zusätzlich trägt die Config-Page aktuell ein generisches Google-Blau
(`#1a73e8`) und hat keinen optischen Bezug zum PhoneBlock-Answerbot — obwohl
der Dongle genau diese Funktion verkörpert.

## Ziel

- `/favicon.ico`-404 eliminieren
- Answerbot-Logo sichtbar im Header der Config-Page platzieren
- Header-/Akzentfarbe an das Logo angleichen

Scope-Grenze: Kein vollständiges UI-Redesign. Nur die Farbe der Primär­aktion
(Header, Button, Stat-Zahlen) wird angepasst. Status-Pills, Tabellen, Layout
bleiben unverändert.

## Design

### Ein SVG für beides

Statt separates Favicon-PNG plus Header-PNG wird eine einzige minifizierte
SVG-Datei embedded und sowohl als Favicon wie auch als Header-Bild referenziert.

- **Vorteil:** Nur ~2 KB Flash, skaliert verlustfrei, ein einziges Binary-Symbol.
- **Kompatibilität:** Moderne Browser (inkl. Android-WebView) akzeptieren
  SVG-Inhalt auch unter dem Pfad `/favicon.ico`, solange der
  `Content-Type: image/svg+xml` gesendet wird und/oder der HTML
  `<link rel="icon" type="image/svg+xml">`-Tag vorhanden ist.

### Quelldatei

Quelle: `phoneblock/src/main/webapp/assets/img/ab/logo/ab-logo-bot.svg`
(Roboter-Kopf in türkiser Chat-Bubble, Farbe `#00d1b2`).

Aufbereitung (manuell oder per Inkscape `--export-plain-svg`):

- `sodipodi:namedview` entfernen
- `inkscape:*`-Attribute entfernen
- Marker-`<defs>` (`Arrow1Lstart`) entfernen — wird vom Logo nicht verwendet
- XML-Deklaration und Kommentare kürzen
- Viewbox und Pfade behalten

Zielgröße: ~1.5–2 KB.

Ablageort: `phoneblock-dongle/firmware/main/web/ab-logo-bot.svg`.

### Firmware: CMakeLists.txt

`phoneblock-dongle/firmware/main/CMakeLists.txt` → `EMBED_FILES` um
`"web/ab-logo-bot.svg"` erweitern. Das erzeugt die Binary-Symbole
`_binary_ab_logo_bot_svg_start` / `_binary_ab_logo_bot_svg_end`.

### Firmware: web.c

Neuer Handler:

```c
extern const uint8_t ab_logo_bot_svg_start[] asm("_binary_ab_logo_bot_svg_start");
extern const uint8_t ab_logo_bot_svg_end[]   asm("_binary_ab_logo_bot_svg_end");

static esp_err_t handle_favicon(httpd_req_t *req)
{
    httpd_resp_set_type(req, "image/svg+xml");
    httpd_resp_set_hdr(req, "Cache-Control", "public, max-age=31536000, immutable");
    return httpd_resp_send(req, (const char *)ab_logo_bot_svg_start,
                           ab_logo_bot_svg_end - ab_logo_bot_svg_start);
}
```

Zwei neue Einträge in `URIS[]` (`web.c:684`), beide auf `handle_favicon`:

- `{ .uri = "/favicon.ico",     .method = HTTP_GET, .handler = handle_favicon }`
- `{ .uri = "/ab-logo-bot.svg", .method = HTTP_GET, .handler = handle_favicon }`

`cfg.max_uri_handlers` wird automatisch mitgezogen (wird per
`sizeof(URIS) / sizeof(URIS[0]) + 2` berechnet).

### Config-Page: index.html

**`<head>`:**

```html
<link rel="icon" type="image/svg+xml" href="/ab-logo-bot.svg">
```

**Header (bisher `<header><h1>PhoneBlock Dongle</h1></header>`):**

```html
<header>
  <img src="/ab-logo-bot.svg" alt="" width="32" height="32">
  <h1>PhoneBlock Dongle</h1>
</header>
```

**CSS-Änderungen am Header** (aktuelle Rule):

```css
header{background:#1a73e8;color:#fff;padding:.8rem 1rem}
```

wird zu:

```css
header{background:#00d1b2;color:#fff;padding:.8rem 1rem;
       display:flex;align-items:center;gap:.6rem}
header img{display:block}
```

### Farbpalette

| Element | Alt | Neu | Begründung |
|---|---|---|---|
| Header-Hintergrund | `#1a73e8` | `#00d1b2` | AB-Türkis (Logo-Farbe) |
| `btn-primary` bg | `#1a73e8` | `#00d1b2` | Konsistenz mit Header |
| `.stat-value` Text auf weiß | `#1a73e8` | `#008f7a` | Dunkleres Türkis für WCAG-AA-Kontrast auf weiß |

Unverändert:
- `.ok` / `.bad` / `.v-SPAM` / `.v-LEGITIMATE` / `.v-ERROR` (semantisch)
- `.btn-primary:disabled` (`#aaa`)
- Fehler-Border (`#f44336`)
- Textfarben, Muted-Töne, Footer

## Größen-Impact

Embedded-Flash:
- Zusatz: ~2 KB (minifiziertes SVG)
- Gesamter `EMBED_FILES`-Zuwachs: ~2 KB

Keine neuen Build-Dependencies, keine neuen Komponenten,
keine NVS- oder API-Änderungen.

## Testen

1. `idf.py build flash monitor` — prüfen, dass kein
   `URI '/favicon.ico' not found` mehr erscheint.
2. Dongle-IP im Browser öffnen:
   - Favicon erscheint im Browser-Tab
   - Logo links neben dem Titel im Header
   - Header und Primär-Button in Türkis
   - Stat-Zahlen in dunklerem Türkis
3. Device-Tools → Network: `/ab-logo-bot.svg` liefert
   `Content-Type: image/svg+xml` und `Cache-Control: public, max-age=31536000, immutable`.

## Nicht im Scope

- Komplett-Redesign der Config-Page
- Anpassung der Status-Pills oder Tabellen-Styles
- Darkmode / Theming-Infrastruktur
- PNG-Fallback für Browser ohne SVG-Favicon-Support
  (alle relevanten Setup-Clients — aktuelle Android-/iOS-Browser — unterstützen es)
