# Domain glossary — The Palace Project (library app)

Argos (the MT engine) has **no domain glossary**. It translates library
vocabulary with generic, often nonsense, meanings. This file is the reference
for the **domain-fix pass**: while auditing the output of
`python3 localization/localize.py domain`, replace any term below that Argos
got wrong with the correct one.

This is the single most important reusable artifact in the pipeline. The MT
output is deterministic-ish: it will make the **same** class of errors on every
fresh run. The glossary is what lets a future run fix them quickly and
consistently.

## How to use

1. Run `python3 localization/localize.py domain` (all four languages) or
   `domain de` / `domain es` / etc. It prints every source string containing a
   domain term, the English source, and the current translation per language
   (entries already fixed are tagged `[override]`).
2. For each string, check the translation against the tables below. If a
   domain term is wrong, write the corrected full string to
   `localization/overrides/<lang>.json` (shape:
   `{ "<module>/src/main/res/values/<file>": { "<stringName>": "<correct text>" } }`).
   Never translate a string you did not change.
3. Re-run `qa <lang>` (must be 0 errors) then `write <lang>`.
4. **If you find a new domain term Argos mistranslates, add it to both this
   glossary and `DOMAIN_TERMS` in `localize.py`** so the next run catches it.

## Library-domain terms

Correct translation per language. The "Argos typically produces" column is the
*signature* to recognize — it is what to look for in the `domain` output.

| English | de | es | fr | it | Argos typically gets wrong (signature) |
|---|---|---|---|---|---|
| hold (a reservation for a book) | Reservierung | reserva | réservation | prenotazione | de `Laderäume` (cargo hold), fr `Prises` (takes), it `Punti` (points), es `bodega` (warehouse) / `sujección` (fastening), fr `cale` (timber block), `attente` (wait) |
| place a hold | (eine) Reservierung aufgeben / reservieren | reservar | (une) réservation placer / réserver | (una) prenotazione fare / prenotare | literal "place a hold" → `Platzierung`, `Lugar de espera`, `Placer la cale`, `Tenere il pulsante` |
| cancel a hold | Reservierung stornieren | cancelar la reserva | annuler la réservation | annullare la prenotazione | `Annullierung` (dropped "hold"), `Annuler la tenue`, `Annulla` |
| you have it on hold | reserviert | en reserva / en espera | en attente / en réservation | in prenotazione / in attesa | de `auf Eis gelegt` (= put on ice) |
| loan (a borrowed book, noun) | Ausleihe | préstamo | prêt | prestito | de `Darlehen` / `Kredit` (bank loan/credit), it `Bordo` (rim) / `Finanziamento` (financing), es `En Loan` (English left in) |
| on loan | ausgeliehen | en préstamo | en prêt | in prestito | de `Auf Darlehen` |
| loans (e.g. "My loans", notification channel) | Ausleihen | préstamos | prêts | prestiti | de `Darlehen`, it `Finanziamenti` |
| borrow (verb) | ausleihen | pedir prestado | emprunter | prendere in prestito | de `Darlehen` (noun, not verb), it `Bordo` |
| borrowing for / borrowed until | Ausleihe für / Ausgeliehen bis | prestado durante / prestado hasta | emprunté pour / emprunté jusqu'au | in prestito per / in prestito fino al | de `Anleihen`, fr `Emprunts jusqu'à` |
| loan limit | Ausleihlimit | límite de préstamo | limite de prêt | limite di prestiti | de `Kreditlimit` (credit limit) |
| return (a book) | zurückgeben | devolver | rendre / retourner | restituire | — |
| returnable | zurückgebbar | devoluble | rendable | restituibile | de `rückzahlbar`, es `reembolsable`, fr `remboursable` (all = *refundable*, i.e. money back) |
| revoke a loan | (die) Ausleihe widerrufen | revocar el préstamo | révoquer le prêt | revocare il prestito | it `Denominazione` (name) |
| patron (a library user) | Nutzer (or Leser) | usuario | utilisateur (usager) | utente (lettore) | de `Patron`/`Patrone` (= patron saint / cartridge!), it `patrono` (godfather), fr `patron` (pattern/priest), es `patrón` (pattern) / `socio` |
| library (a lending library) | Bibliothek | biblioteca | bibliothèque | biblioteca | **it `libreria` = bookshop (where you BUY) — wrong for a lending library** |
| libraries | Bibliotheken | bibliotecas | bibliothèques | biblioteche | it `librerie` (bookshops) |
| library card | Bibliothekskarte / Bibliotheksausweis | tarjeta de biblioteca | carte de bibliothèque | carta di biblioteca | — |
| due (date) | Fällig am / Fälligkeit | Vence el / vencimiento | Échéance | Scadenza | de `Wegen` (= because of!), fr `%1$s attendu` |
| reservation(s) | Reservierung(en) | reserva(s) | réservation(s) | prenotazione(i) | de `Vorbehalte` (= objections/prejudices!), fr `Réserves` (= reserves) |
| feed (an OPDS catalog feed) | Feed | feed | flux | feed | **es `alimentación`/`alimento`/`pienso`, it `mangimi`, de `Futter`/`Beladefutter` (all = FOOD)** |
| my books (the shelf of saved/borrowed books) | Meine Bücher | Mis libros | Mes livres | I miei libri | — |
| checkout drawer (the loan-management drawer) | Checkout Drawer (loanword) | cajón de préstamo | tiroir d'emprunt | cassetto di checkout | fr `tiroir de caisse` (cash drawer), es `caja`/`cajero` (cash register/ATM), it `sportello` (counter) |
| bookmark (saved book) | Lesezeichen | marcador | marque-page | segnalibro | es `marca(s) registrada(s)` (= trademark!), fr `signet` is OK but `marque-page` preferred |
| password | Passwort | contraseña | mot de passe | parola d'ordine | it `password` left in English (acceptable loanword, but `parola d'ordine` preferred in UI labels) |
| parse / parsing (e.g. "Parsing a OPDS feed") | analysieren/parst | analizar/analizando | analyser/analyse | analizzare/analisi | it `parso` (= parsley, the herb!), de `Parade` (review) |

## Common UI words (second audit class — also check every run)

The `domain` audit is driven by `DOMAIN_TERMS` in `localize.py`, which
contains **two** classes: library-domain terms (above) and these common UI
words that Argos systematically mistranslates. They contain no library
vocabulary, so an audit that only looks at domain terms misses them entirely
(this is exactly how `Protokoll`/`Jugar`/`Jouer`/`Processo verbale` for
"minutes" survived for a whole session).

| English | de | es | fr | it | Argos signatures |
|---|---|---|---|---|---|
| minutes (time unit) | Minuten | minutos | Minutes | minuti | **de `Protokoll` (meeting minutes), fr `Procès-verbal`, it `Processo verbale`** — all = official written record of a meeting |
| seconds | Sekunden | segundos | secondes | secondi | fr `Deuxièmes`/`Deuxième` (ordinal/fraction), de `Zweite` (second person) |
| play (media verb/button) | Abspielen | reproducir | Lecture | riproduci | **de `Spiel`, es `Jugar`, fr `Jouer`, it `Gioca`/`Giocato` — all = to play a GAME** |
| back (button) | Zurück | Atrás | Retour | Indietro | de `Rücken` (the body part "back"), fr `Précédent` (= previous) |
| remaining (time) | verbleibend | restantes | restant | rimanenti | de `bleibt bestehen` (= remains valid), es `Permaneciendo` (= lingering) |
| rewind | zurückspulen / zurück | retroceso / rebobinar | reculer / remonter | riavvolgi | it `Rewind` left in English, de `30 Sekunden` (action word dropped) |
| fast forward | vorwärts / Vorlauf | avance rápido | avance rapide | avanti veloce | it `Veloce in avanti` (word salad) |
| cover (of a book) | Buchumschlag | portada | couverture | copertina | es `Cubierta` (lid/board/surface), it `Copertura` (insurance coverage) |
| hour (time unit) | Stunde | hora | heure | ora | it `Orario` (= schedule/time zone) |
| sync (a11y button labels) | Synchronisation | sincronización | synchronisation | sincronizzazione | es `Sincronizador` (= the synchronizer, a person/thing) |

Note the shape of the errors: Argos picks a *plausible* word with a different
sense (meeting-minutes, game-play, body-part, schedule). A translation can be
grammatical and still wrong. That is why the signatures — not just the
correct forms — are recorded here.

## Systematic Argos artifacts (check every run)

- **Trailing double dot `..` instead of `…`** (and mid-string ` .. ` for
  sentence-ending ellipses). Affects dozens of strings per language on every
  fresh run. Sweep: any translated value containing `..` where the source has
  `…` (or `...`) → replace with `…`. The QA step does not catch this.
- **English left in** (short tokens survive untranslated): e.g. de `Sync
  Bookmarks`, `Cache Directory`, `Preview Button`; it `Rewind 30 secondi`,
  `Reboot In Nuevo Modo UI`; es `Saltear adelante` (non-word). Mechanical
  heuristic in `qa`: a translated value (different from source) still contains
  ≥50% of the source's letter-runs >2 chars, after removing
  `VERBATIM_FILTER` (brands/acronyms/loanwords) → review flag. Tune the
  filter, not the threshold; every filter entry must also be documented here.
- **Programmatic ids must stay identical**: `notification_channel_id_*`
  values are read back by the code as Android notification-channel ids
  (`context.getString(R.string.notification_channel_id_*)` in
  `MainNotificationResources.kt`). They are in `VERBATIM_OK`; if MT mangles
  them, override them back to the exact source value.
- **Garbled/over-shortened output**: e.g. it `E' in ritardo` for
  "Logout started", es `Failed totch`, fr `signer` for a sign-out confirm,
  fr `session a expiré` (acceptable French — do NOT "fix").

## Leave verbatim (never translate)

- **Brand / product names**: `Palace`, `Adobe`, `ACS`, `Readium`, `pdfjs`,
  `OpenEBooks`, `Crashlytics`, `Android`.
- **Protocols / acronyms**: `OPDS`, `LCP`, `DRM`, `SAML`, `OIDC`, `URI`, `QA`.
- **File formats**: `EPUB`, `PDF`, `eBook`/`E-Book`/`ebook` (keep the form the
  source uses).
- **UI universal**: `OK`, `FAQ`, `Wi-Fi`.
- **Single-letter interval markers**: `d` `h` `m` `w` `y` (days/hours/…).
- **Numbers / time literals**: `00:00:00`, `30`.
- **Technical ids**: `simplified-viewer-epub-readium2`.
- **Programmatic ids**: `channel_id_loans`, `channel_id_reservations`
  (see artifact note above).
- **Words identical in source and target** (QA suppresses these as
  "known verbatim"; they are correct — do NOT "fix" them): de `Information`,
  `Format`, `Filter`, `Pause`, `Minute`, `Passphrase`, `Crash`, `Filter
  (%1$d)`; fr `Documentation`, `Notifications`, `Pause`, `Minute`, `Minutes`,
  `1 minute`; it `Pause`. The list lives in `VERBATIM_OK` in `localize.py`
  — it and this section must be kept in sync.

## Acceptable loanwords (fine to leave in source form)

Mechanically: `VERBATIM_FILTER` in `localize.py` (lowercase). Keep it in sync
with this list; every entry here must be there and vice versa.

- Brands/protocols/technical: `download`, `player`, `ebook`, `e-book`, `wifi`,
  `app`, `feed`, `android`, `readium`, `adobe`, `acs`, `open ebooks`, `opds`,
  `lcp`, `drm`, `saml`, `oidc`, `openid`, `connect`, `sso`, `web`, `uri`,
  `url`, `eula`, `database`, `qa`, `id`.
- Established loanwords in the target languages: `debug`, `analytics`,
  `support`, `offline`, `details`, `provider`, `account`, `card`, `creator`,
  `time`, `tracking`, `audio`, `engine`, `channel`, `options`, `sync`,
  `privacy`, `report`, `core`, `checkout`, `drawer`, `software`, `directory`,
  `cache`, `table`, `manual`, `description`, `position`, `mode`, `password`,
  `minutes`, `seconds`, `hour`, `minute`, `second`.
- de-specific: `button` (a11y labels use "X-Button").
- Place names (proper nouns): `new`, `york` (residual risk: a genuinely
  untranslated "new" in a 2-word string would not be flagged — accepted).
- fr: use `flux` for "feed" (preferred), not the English loanword.

## Register / person — DECIDED (formal everywhere; overrides 2025-09 tú/tu)

| lang | register | note |
|---|---|---|
| de | formal `Sie` | standard for apps |
| fr | formal `vous` | standard |
| es | formal `usted` | user decision: formal wherever there is a choice |
| it | formal `Lei` | user decision: formal wherever there is a choice |

"Wherever there is a choice": neutral 3rd-person forms (which double as the
informal imperative for regular -AR verbs — it `Mostra`, `Annulla`, `Crea`,
es `Cierra`, `Elimina`, `Encuentra`) are register-NEUTRAL and are kept;
user-directed sentences use the formal 2nd person (it `Lei`: `Tocchi`,
`Inserisca`, `ha`, `suo`; es `usted`: `Toque`, `Ingrese`, `ha`, `su`).

`qa` mechanically flags the UNAMBIGUOUS informal forms (word lists in
`INFORMAL_WORDS` in `localize.py`: it `tu/tuo/.../hai/leggi/chiudi/...`,
es `tu/tus/estás/has/tienes/quieres/...`, de `du/dich/...-st`, fr
`tu/ton/ta/tes`). Known limits, do not "fix" these:
- es/it regular-verb tú-imperatives are identical to the 3rd person
  (`Tocca`/`Cierra`) and cannot be flagged without false positives; check
  by eye in the domain audit. es `se pulsa` (3rd-person passive) and `ve`
  (= he sees / tú imperative) are deliberately excluded.
- it `Tocca questo pulsante` read as a user-directed sentence is a tu form;
  the a11y convention below uses the formal `Tocchi`.

## Accessibility button-label convention

The `bookPreviewAccessibility*` / a11y button strings follow a fixed shape
per language (name, then the action): de `…-Schaltfläche. Tippen Sie auf
diese Schaltfläche, um …`, es `Botón …. Toque este botón para …`, fr
`Bouton …. Cliquez sur ce bouton pour …`, it `Pulsante …. Tocchi questo
pulsante per …` (formal). Keep this shape in future edits.

Shorter a11y labels (no "tap to" clause) use the per-language noun for
button: de `X-Button` (e.g. `Kontoauswahl-Button`, `Details-Button`),
es `Botón de X`, fr `Bouton X`, it `Pulsante X`.

## Decision log

- **Register reversal (user decision)**: formal register everywhere there is
  a choice — es `usted` (was `tú`), it `Lei` (was `tu`); de `Sie` and
  fr `vous` unchanged. Full sweep migrated 120+ strings (it ~94, es ~36).
  Mechanical check inverted: `INFORMAL_WORDS` now flags informal 2nd-person
  forms as review items. New tripwires: de `Spielmuster` (MT compound for
  "Play Sample"), es `fallado` (ungrammatical "failed"), it `gioco` ("game";
  MT mistranslated "Play Sample" as "Gioco di esempio").
- **2025-09 (mechanical gate)**: QA gained content checks — `..` artifact
  (error), `BAD_TERMS` signatures (review), english-left-in heuristic
  (review), es-`usted`/it-`Lei` register scan (review), known-verbatim
  suppression. A `verify <lang>` command re-parses the written XML and
  byte-compares every aapt2-decoded value against the merged translations.
  `DOMAIN_TERMS` gained the "Common UI words" class above. Consequence: a
  clean `qa` (0 errors **and** 0 reviews) plus a clean `verify` is now a
  meaningful mechanical gate, not just a placeholder check.
