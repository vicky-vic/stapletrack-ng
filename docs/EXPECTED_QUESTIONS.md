# StapleTrack NG — Expected examiner questions

Fifteen likely questions with prepared answers. Answer in your own words; these are the facts and the reasoning to hit, not a script to recite.

---

## Data model & storage

### Q: Why three tables instead of one?

NBS publishes three different shapes of data — national averages for three months, one price per geopolitical zone, and the single highest and lowest state per item. A single polymorphic table would need a discriminator column plus zone, state and kind columns that are empty for most rows. Three narrow tables model what NBS actually publishes, each with its own natural unique key — (item, month), (item, month, zone), (item, month, kind) — and each query only touches the rows it needs.

### Q: Why VARCHAR(7) for month_year?

The data is monthly, and `java.time.YearMonth` serialises as `YYYY-MM` — exactly seven characters. A DATE column would force an invented day of the month and misrepresent monthly figures as daily ones. A separate year and month integer pair works but makes sorting and range filters clumsier. Because the string is zero-padded, it sorts chronologically and `BETWEEN '2024-01' AND '2024-10'` just works. A JPA `AttributeConverter` maps it to `YearMonth` in Java.

### Q: How does upsert work without race conditions?

For each parsed row, the import looks the row up by its unique key; if it exists it updates the price, otherwise it inserts. The whole file runs in one transaction. Uploads are a single-user admin action here, and the database's unique constraints are the final safety net — a concurrent duplicate insert would fail rather than create a second row. For a multi-user version I would move to MySQL's `INSERT … ON DUPLICATE KEY UPDATE` so the database resolves the conflict in one statement.

---

## Forecast model

### Q: Why linear regression and not ARIMA or Prophet?

The brief required pure Java with no ML library, and linear regression is small enough to implement and verify by hand. It also suits the data: with 12–23 monthly points, ARIMA's extra parameters would overfit, and Prophet's seasonality needs several years of history. I got more value from showing two fitting windows than from a more complex model.

### Q: What does R² mean here?

It is the share of the month-to-month variation in price that the straight-line trend explains. For rice over the full history R² is 0.92: about 92% of the variation follows the linear trend and the remaining 8% is movement the line cannot explain. The two windows' R² values aren't directly comparable — each measures fit to its own window, and a short window can fit tightly and still be a poor guide.

### Q: Why 95% confidence bands specifically?

z = 1.96 is the statistical convention for a two-sided 95% interval. The band is a prediction interval: if prices really follow a straight line with roughly normal scatter around it, about 95% of future months should land inside. It widens further out because errors in the fitted slope grow with distance. The "if" matters — a fuel-price shock breaks that assumption, which is why the page says so.

### Q: Why does the recent-trend forecast start above the last actual point?

The last few months grew more slowly than the middle of 2024 — rice rose only about ₦30 from September to October. The 12-month line still carries the steeper mid-year slope, so its first projected step overshoots. The full-history line has the opposite problem and starts below today's price. The comparison sentence on the page puts the two side by side so the user can see the disagreement.

---

## Design & UX

### Q: Why Fraunces and DM Sans?

I wanted an editorial, data-journalism feel — the style of outlets like The Pudding and FiveThirtyEight — rather than a generic app dashboard. A warm serif for headings signals that this is about real families and food; a clean sans keeps body text and numbers readable. Both are open-licence fonts served from Google Fonts.

### Q: Why the amber dot in the navbar?

It is a memorable brand mark that costs one line of CSS — a pseudo-element before the wordmark — and uses the same amber as the rest of the palette. It shows attention to detail without needing a logo file.

### Q: Why show gaps in the charts instead of interpolating?

Interpolating would invent prices NBS never published. A gap tells the user something true: no release I loaded covers that month (for example May 2023, which would only come from the May 2024 file). The forecast also uses real month spacing, so the gap isn't silently squeezed out of the regression.

---

## Security & robustness

### Q: What if someone uploads a malicious xlsx?

Apache POI 5.5.1 includes the fix for CVE-2025-31672, an OOXML parsing flaw — I upgraded from 5.2.5 after a dependency review. The upload checks the file extension, and the parser then has to open it as a real workbook: a renamed text file fails with a clear user-facing message ("could not be read as an Excel workbook") instead of a stack trace. Spring's default upload size limit (1 MB; NBS files are about 23 KB) also caps what can be sent.

### Q: What about XXE in the SVG map?

`NigeriaMapRenderer` parses the SVG with a hardened XML parser: doctype declarations are rejected, and external entities, external DTDs and XInclude are all disabled. A malicious replacement map can't use entity references to read files from the server. There's a unit test that feeds it a doctype with an external entity and expects it to be rejected.

### Q: What if MySQL goes down mid-upload?

The import runs inside a single `@Transactional` method, set to roll back on any exception. If the database connection drops or any row fails, nothing from that file is committed — there is no half-imported month. The user sees an error message and can simply upload the file again, and the upsert logic means a re-upload never creates duplicates.

---

## Data quality

### Q: How do you handle NBS spelling variance?

Two layers. At import, the parser turns underscores in state names into spaces ("Akwa_Ibom" → "Akwa Ibom"). At read time, `StateNames` maps known variant spellings to one form — "Nassarawa" → "Nasarawa" — before display. Doing the variant mapping at read time means stored rows stay exactly as NBS published them, and a new variant tomorrow is a one-line addition to the map.

### Q: What if a monthly release format changes?

The parser identifies sheets by their header content, not by tab position or name, because NBS has already reversed the tab order and renamed sheets between releases. Within a sheet, columns are found by header text; the item column accepts several spellings ("Item Label", "ItemLabel", "Items", …) and falls back to column A with a warning if none match. Bad rows or cells become warnings, shown in a collapsible list under the upload message, rather than failing the whole file. The one hard requirement is the "Average of MMM-yy" month headers — without them the file is rejected with a clear message, because the release month can't be determined.
