# StapleTrack NG — Defense script

> **Rehearse this out loud 2–3 times before defense day. Speaking rate should feel unhurried — if you finish in under 4 minutes total, slow down.**

Target timing: Problem ~30 s · Demo ~3 min · Technical decisions ~1 min · Reflection ~30 s.

Before you start: have the app running at `http://localhost:8080` with all 11 releases loaded, and open the landing page in a browser tab. Zoom the browser to 110–125% so the room can read it.

---

## 1. Problem (≈30 seconds)

Food inflation is the biggest day-to-day issue Nigerian families face. Every month the National Bureau of Statistics publishes what staples cost across the country — but it arrives as an Excel file, and most people never open it. Even if you do, one release only shows three months at a time. I built StapleTrack NG to stitch those monthly releases together, so anyone can see how prices have moved, where they're highest, and where they're heading.

---

## 2. Live demo walkthrough (≈3 minutes)

1. **Landing page** (`/`).
   Say: *"This is what a Nigerian household would land on. Two paths — upload NBS data, or explore what's already there."*

2. **Upload page** (`/upload`).
   Say: *"The app takes NBS's own Excel files, unmodified. I built the parser to handle the quirks across different release months."*
   *(If asked: older files put the zone sheet first and label columns differently — the parser finds sheets and columns by their content.)*

3. **Dashboard** (`/dashboard`).
   Say: *"Twenty-three months of national prices for rice, beans, and yam. The dashed tail is a three-month forecast — we'll come back to that."*
   *(Point at the gap in May 2023: "That gap is honest — no release I loaded covers that month, so I don't draw a line through it.")*

4. **Extremes page** (`/extremes`) — Rice local sold loose, Oct 2024.
   Say: *"Same staple, different question — which state paid the least and the most in a given month?"*
   *(On screen: Benue ₦1,267 vs Kogi ₦2,693 — "rice cost 2.1 times as much in Kogi as in Benue.")*

5. **Zones page** (`/zones`) — Rice, Oct 2024, then Tomato, Feb 2024.
   Say: *"The country coloured by regional prices. The map answers 'where', the bars answer 'how much', both share the same scale."*
   *(Rice: North West cheapest, South East dearest, 22% apart. Switch to Tomato / Feb 2024: "a much wider gap — the South South paid 2.5 times what the North West paid.")*

6. **Forecast page** (`/forecast`) — Rice.
   Say: *"Linear regression, pure Java, no ML library. Two windows because a straight line across a regime change under-projects the current trend — I show both so the user judges which to trust."*
   *(Point at the two rows: full history projects ₦1,913 for Nov 2024, the last 12 months ₦2,155. Open "About this forecast" if there's time.)*

---

## 3. Technical decisions worth calling out (≈1 minute)

Volunteer these even if nobody asks — they show engineering judgement, not just features:

- **Security review of dependencies.** Apache POI 5.2.5 has CVE-2025-31672 in exactly the code path the upload uses (OOXML parsing). I caught it during dependency review and shipped 5.5.1 instead.
- **Dual-window forecast.** Ordinary least squares across a regime change — slow 2023, steep 2024 — under-projects: the full-history line starts below today's price. A 12-month window follows the current slope. I show both rather than hide the trade-off.
- **XXE hardening on the map renderer.** The Nigeria SVG is parsed and re-serialised on every request, so the XML parser has DTDs and external entities disabled — a tampered map file can't read server files.
- **Cross-language check of the maths.** I recomputed the rice regression independently in Python; slope, R² and every forecast value match the Java output to the naira.
- **Read-time canonicalisation.** NBS spells Nasarawa two ways ("Nassarawa"). I fix it when reading for display, not by rewriting stored rows, so the database stays a faithful copy of what NBS published.
- **Three-entity data model.** `NationalPrice`, `ZonalPrice` and `StateExtreme` mirror the three shapes NBS actually publishes, instead of one polymorphic table full of empty columns.
- **Content-based sheet detection.** I discovered that older NBS files reverse the tab order and rename the summary sheet, so the parser identifies sheets by what's in their header row, not by position or name.

---

## 4. Reflection (≈30 seconds)

**Limitation:** *"The forecast assumes recent trends continue. It can't see a fuel-price shock or a harvest failure coming. Neither can any model working from prices alone."*

**Next step:** *"State-level time series. NBS holds it internally but doesn't publish it monthly. If they did, the extremes page would become a full state ranking rather than just cheapest and priciest."*

---

### Numbers to have ready

| Fact | Value |
|---|---|
| Releases loaded | 11 (Nov 2023 – Oct 2024, no May 2024 file) |
| National price history | 23 months, Nov 2022 – Oct 2024 (gap: May 2023) |
| Items | 43 |
| Rice, Oct 2024 national average | ₦1,944.64 |
| Rice forecast, Nov 2024 | ₦1,913 full history (R² 0.92) · ₦2,155 last 12 months (R² 0.98) |
| Automated tests | 83, all passing |
