# 0012. Permanenter Titel-Cache vs. TTL-basierter Verfügbarkeits-Cache

- **Date**: 2026-07-28
- **Status**: Accepted

## Context

Die App cached zwei fachlich unterschiedliche Dinge in der DB, beide global (nicht pro Nutzer,
über alle Watchlists hinweg geteilt):

1. **Titel-Metadaten** — Poster-Bild, FSK-Altersfreigabe, deutscher Titel (`title_meta`,
   `title_poster`), gespeist von `TitleMetaService`/`PosterService` aus **einem** IMDb-GraphQL-Fetch
   pro Titel (`ImdbTitleClient`).
2. **Streaming-Verfügbarkeit** — welcher Anbieter den Titel wie (Flatrate/Kauf/Leihe, Preis, Sprache)
   anbietet (`query_meta`, `query_result`), gespeist von `StreamInfoService` aus dem
   werstreamt.es-Scraping.

Beide Tabellen sehen strukturell ähnlich aus (`imdb_id`-Schlüssel, ein Zeitstempel), verhalten sich
aber fundamental unterschiedlich: Titel-Metadaten (Poster, FSK-Freigabe, Originaltitel) sind mit der
Veröffentlichung eines Films fixierte, praktisch unveränderliche Fakten. Streaming-Verfügbarkeit
ändert sich dagegen laufend — Lizenzen laufen aus, Anbieter wechseln, Preise ändern sich — und muss
regelmäßig neu abgefragt werden.

Der Architektur-Review vom 2026-07-28 (`docs/ARCHITECTURE_REVIEW.md`, Fund F7) bemängelte zunächst,
dass `title_meta`/`title_poster` keine Ablaufsteuerung/kein Cleanup für positive Treffer haben. Das
ist für den Titel-Cache jedoch **beabsichtigtes Verhalten**, kein Defizit — bislang aber nirgends
explizit festgehalten, nur implizit aus Javadoc-Kommentaren in `TitleMetaService`/`PosterService`
erkennbar ("A positive result is permanent"). Dieses ADR macht die Entscheidung explizit.

## Decision

Es gibt bewusst **zwei unterschiedliche Caching-Strategien** für die beiden Datenarten.

**1. Titel-Metadaten-Cache (`title_meta`, `title_poster`) — dauerhaft gültig, kein TTL für positive
Treffer.**

- Ein positiver Treffer (Poster gefunden, Rating gefunden, deutscher Titel gefunden) gilt als
  **permanent**: kein TTL, kein automatischer Refresh, keine geplante Bereinigung
  (`TitleMetaService.classify`/`PosterService.classify`: eine positive Zeile hat schlicht keinen
  Alters-Check). Diese Daten sind statische Fakten zu einem Filmtitel.
- Nur ein **negatives** Ergebnis ("kein Poster/Rating gefunden") bekommt ein TTL
  (`poster.negative-cache-days`, Default 14 Tage, `isNegativeFresh(...)`) — nach Ablauf wird erneut
  versucht, weil ein Nicht-Fund öfter ein temporäres Problem (Rate-Limit, IMDb-Fehler, Titel noch
  nicht erfasst) als ein dauerhafter Fakt ist.
- Kein Cleanup-Job, keine Kopplung an die Watchlist: verschwindet ein Titel aus jeder Watchlist,
  bleibt sein Cache-Eintrag bestehen. Gewollt — die Daten sind global gültig, und ein erneuter
  Watchlist-Import desselben Titels profitiert sofort vom bestehenden Cache statt eines neuen
  IMDb-Requests.

**2. Streaming-Verfügbarkeits-Cache (`query_meta`, `query_result`) — TTL-basiert + manuelle
Invalidierung.**

- Ein Treffer gilt nur `wer-streamt.invalidate.after-days` Tage (Default 28) als frisch
  (`StreamInfoService.isFresh`), danach wird beim nächsten Zugriff automatisch neu gescraped.
- Zusätzlich kann ein ADMIN Einträge gezielt vorzeitig invalidieren (`POST /api/manage/invalidate`,
  setzt das `invalidated`-Flag auf `query_meta`) und gezielt neu scrapen lassen
  (`POST /api/manage/scrape-invalidated`, `/manage`-UI) — etwa wenn bekannt ist, dass sich ein
  Angebot geändert hat, bevor die 28 Tage um sind.

## Consequences

**Einfacher / vorteilhaft:**

- Klare gedankliche Trennung: "was **ist** der Film" (permanent) vs. "**wo läuft er gerade**"
  (flüchtig, muss aktuell gehalten werden).
- Titel-Metadaten-Fetches sind auf **einen** IMDb-Request pro Titel über die gesamte Lebensdauer
  einer Installation begrenzt (abgesehen von negativen Retries alle 14 Tage) — spart IMDb-Requests
  massiv gegenüber einem TTL-Ansatz für ohnehin unveränderliche Daten.
- Ein Titel, der erneut auf eine Watchlist gelangt (eigene oder fremde), muss nie erneut nach Poster
  oder FSK-Freigabe fragen.

**Nachteile / bewusst in Kauf genommen:**

- `title_poster`-BLOBs (Thumb + Full pro Titel) wachsen unbegrenzt mit; es gibt keinen Cleanup, wenn
  ein Titel aus jeder Watchlist verschwindet. Bei der aktuellen Nutzungsgröße (persönliche
  Watchlists, keine Massendaten) vertretbar. Sollte die Datenmenge relevant werden, wäre ein
  administrativer Cleanup-Endpunkt ("lösche Cache-Einträge für Titel, die auf keiner Watchlist mehr
  stehen") die naheliegende Erweiterung — bewusst nicht gebaut, solange kein Bedarf besteht (YAGNI).
- Ändert sich ein bereits gecachtes Poster oder Rating bei IMDb doch einmal (z. B. Korrektur eines
  falschen FSK-Werts), gibt es aktuell keinen Weg, das zu erzwingen, außer den DB-Eintrag manuell zu
  löschen — anders als beim Verfügbarkeits-Cache gibt es keine ADMIN-UI-Invalidierung für
  Titel-Metadaten. Akzeptierter Trade-off, kein offener Bug.
