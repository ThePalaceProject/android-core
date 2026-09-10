# Localization runbook — The Palace Project (Android)

This is the complete procedure to (re)produce the German, Spanish, French, and
Italian string resources from scratch, with **no human review and no cloud
service**. A future instance of the agent (model + harness) should read this
top to bottom and execute it. The MT engine is local (LibreTranslate/Argos);
the agent itself is the fix layer for everything MT gets wrong.

- Target languages: `de es fr it`
- Source language: `en`
- Script: `localization/localize.py` (zero regex; all character scanning)
- Glossary (domain terms): `localization/glossary.md`  ← read this before the fix pass

## What "done" means

Per language:
1. `qa <lang>` reports **0 errors and 0 reviews**. Reviews are triage items,
   not acceptable residue: each must be fixed (override) or, if it is a
   legitimate verbatim/loanword, added to `VERBATIM_OK`/`VERBATIM_FILTER` in
   `localize.py` **and** documented in `localization/glossary.md`. The
   "known verbatim suppressed" count on the `qa` line is the mechanism that
   makes 0-reviews reachable: legitimate identical/loanword strings are
   suppressed, everything else must be dispositioned.
2. Every `domain` string is contextually correct (audited against the glossary,
   both term classes: library-domain **and** common UI words).
3. `write <lang>` emits `<module>/src/main/res/values-<lang>/*.xml`, every file
   well-formed (parses), 22 files, no empty strings except those whose source is
   itself empty.
4. `verify <lang>` passes: every written file re-parses, every aapt2-decoded
   value matches the merged translation exactly, and no `..` artifact remains.

The realistic guarantee is "very likely acceptable for the common library
terms" plus the common-UI-words class in the glossary, with mechanical gates
at every stage. Native-speaker verification is out of scope.

---

## 0. Prerequisites

- Python 3 (the script uses only the stdlib: `json`, `urllib`, `xml.etree`, `pathlib`).
- A running **LibreTranslate** server (Argos backend). It is normally a local
  podman container named `libretranslate`.

### Bring up LibreTranslate

```sh
# Is it already up?
podman ps --filter name=libretranslate --format '{{.Names}} {{.Ports}}'
```

If it's up, get its host port (it is **ephemeral**, e.g. `39265`):

```sh
PORT=$(podman ps --filter name=libretranslate --format '{{.Ports}}' | sed -n 's/^[0-9.]*:\([0-9]*\)->.*/\1/p')
# -> e.g. 39265
curl -s "http://127.0.0.1:$PORT/health"    # expect {"status":"ok"}
```

If it's not up, start it (downloads language models at first start — needs
internet only the first time; models live in the container's writable layer):

```sh
podman run -d --name libretranslate \
  -p 127.0.0.1::5000/tcp \
  docker.io/libretranslate/libretranslate:latest \
  --load-only en,de,es,fr,it
# wait for health, then:
PORT=$(podman ps --filter name=libretranslate --format '{{.Ports}}' | sed -n 's/^[0-9.]*:\([0-9]*\)->.*/\1/p')
```

> To restrict model load use `--load-only en,de,es,fr,it` (faster, smaller).
> Omitting it loads all languages. The forward pairs the pipeline needs are
> `en_de en_es en_fr en_it`; the reverse pairs are only needed if you add a
> back-translation QA gate (see "Optional" below).

Export the base URL once so every command below picks it up:

```sh
export LIBRETRANSLATE_URL="http://127.0.0.1:$PORT"
```

(The script reads `LIBRETRANSLATE_URL`, defaulting to `http://127.0.0.1:5000`.
You can also pass `--base-url` per command.)

---

## 1. Extract (regenerate the manifest from current source)

```sh
python3 localization/localize.py extract
```

Re-parses every `*/src/main/res/values/*string*.xml` and writes
`localization/out/manifest.json`. **Always run this first** when strings may
have changed — the manifest is the source of truth for the rest of the run.

Apply `aapt2_decode` to each value (see "Invariants"), so the manifest stores
the *logical* display text. Skipped (non-translatable) entries are recorded with
a reason: `@string/` references, lorem-ipsum, and numeric-only arrays.

## 2. Translate (fresh MT for each language)

```sh
for lang in de es fr it; do
  python3 localization/localize.py translate "$lang"
done
```

Calls LibreTranslate in batches, writes `localization/out/translations/<lang>.json`.
Placeholders are shielded with sentinel tokens so Argos copies them verbatim.

## 3. QA gate (objective breakage)

```sh
for lang in de es fr it; do
  python3 localization/localize.py qa "$lang"
done
```

Flags, per string:
- **error**: missing (no translation), empty (non-empty source → empty target),
  placeholder mismatch (positional compared as a sorted multiset, non-positional
  `%s`/`%d` compared as an ordered sequence), or a `..` double-dot artifact
  (no source string contains `..`, so any in a target is the `…` ellipsis
  artifact). Also: **any misplaced or stale override key is an error** —
  `qa` refuses to pass while the override file points at the wrong file or at
  a string that no longer exists.
- **review**: translation identical to source (suppressed when the value is in
  `VERBATIM_OK` — `Palace`, `OK`, `EPUB`, `d`, `00:00:00`, channel ids, … —
  and counted as "known verbatim suppressed"), a known Argos error signature
  (`BAD_TERMS` in `localize.py`, kept in sync with the glossary), an
  english-left-in heuristic hit (≥50% of source letter-runs >2 chars survive,
  after removing `VERBATIM_FILTER`), or an informal-register violation (the
  decision is FORMAL everywhere: es `usted`, it `Lei`, de `Sie`, fr `vous` —
  `INFORMAL_WORDS` in `localize.py` lists the flaggable informal forms).

**`qa` must reach 0 errors *and* 0 reviews before `write`.** Every review is a
triage item: fix it via an override (steps 4/5), or — if it is a legitimate
verbatim/loanword — add it to the right list in `localize.py` **and** document
it in `localization/glossary.md` (the lists and the glossary are one knowledge
base in two places; they must stay in sync).

## 4. Domain-fix pass (the part MT cannot do)

This is where most of the real work is. MT has no glossary, so it mistranslates
library vocabulary.

The audit surface is `DOMAIN_TERMS` in `localize.py`, which covers **two**
classes (both documented in the glossary): library-domain terms (loan, hold,
patron, feed, …) and common UI words that Argos systematically mistranslates
(minutes, seconds, play, back, …). Do not narrow the audit to library terms —
the preview-player a11y labels (`Protokoll`/`Jugar`/`Jouer`/`Processo
verbale` for "minutes") contain no library vocabulary at all.

1. **Read `localization/glossary.md`** first.
2. Run the audit for each language (or all):

   ```sh
   python3 localization/localize.py domain          # all four
   # or
   python3 localization/localize.py domain de
   ```

   It prints every source string containing a domain term, the English, and the
   current translation per language. Entries already corrected are tagged
   `[override]`.
3. For each string, compare the translation to the glossary tables. When a
   domain term is wrong, rewrite the **whole** string with the correct term and
   record it in `localization/overrides/<lang>.json`.
4. Also fix any short-token mangles QA's review list surfaced (e.g. `EPUB`→
   `EPUBBLICA`, `OK`→`TRÈS BIEN`, `y`→`sí`, duplicated `00:00:00`).

### Overrides file format

`localization/overrides/<lang>.json` (outside `out/` -- it is the only
non-regenerable artifact, so it must survive `out/` being gitignored):

```json
{
  "palace-ui/src/main/res/values/strings.xml": {
    "tabHolds": "Reservierungen",
    "accountCancel": "Abbrechen"
  }
}
```

Keyed by `<module>/src/main/res/values/<file>` → `<stringName>` → corrected
 text. For a `<string-array>`, the value is a JSON list of items. `write`
merges overrides **on top of** the MT output, so overrides always win. Merge,
don't overwrite: if you add a fix, keep the existing entries in the file.

> **Key by the file the string actually lives in.** `palace-ui` splits its
> strings across many files (`stringsCatalog.xml`, `stringsFeed.xml`,
> `stringsLogin.xml`, ...). An override key is only effective if its path
> matches the manifest path of the named string; a misplaced key is silently
> dropped. `qa`/`write` now print a `WARNING: override ... has NO effect`
> line for any misplaced key — treat those warnings as errors and re-key the
> entry. When in doubt, find the string's file in `manifest.json`.

> **Re-run caveat.** If a source string's *value* changed since the last run,
> an existing override for that name may no longer fit. The `domain` audit shows
> both the current source and the current (possibly overridden) value — if an
> `[override]` value no longer reads as a correct translation of the *new*
> source, rewrite or delete it.

## 5. Re-run QA, then write

```sh
for lang in de es fr it; do
  python3 localization/localize.py qa "$lang"          # must be 0 errors
  python3 localization/localize.py write "$lang"
done
```

`write` re-parses each emitted file for well-formedness and writes
`<module>/src/main/res/values-<lang>/<basename>.xml` mirroring the source tree.

## 6. Verify (post-write gate)

```sh
for lang in de es fr it; do
  python3 localization/localize.py verify "$lang"
done
```

`verify` re-parses every written `values-<lang>/*.xml`, aapt2-decodes every
value, and compares it byte-for-byte against the merged translation
(MT + overrides). It also re-checks the `..` artifact on the written files.
Exit 1 on any drift. This is the gate that catches hand-edits to the XML, a
half-finished `write`, or a stale tree: after `write`, `verify` must pass,
and if anyone later edits the XML by hand, `verify` tells you the file no
longer matches the pipeline's state (re-derive: fix the override, re-run
`write`).

Expect 22 files per language, 0 parse errors. Empty strings are acceptable only
where the *source* is itself empty (e.g. the `settings*Summary` strings, which
are `<string name="…"/>` in the source and are set at runtime).

---

## Invariants (do not break these)

- **No regular expressions.** All text handling in `localize.py` is explicit
  character scanning. If you must add logic, keep it regex-free.
- **Never localize a string referenced from an `AndroidManifest.xml`.**
  See the dedicated section below (lint `ManifestResource`, fatal on
  release builds).
- **Escape model.** Read side = `aapt2_decode` (inverts aapt2: unescapes
  `\' \" \\ \n \t \uXXXX`, folds whitespace runs to one space, strips an
  enclosing unescaped `"` pair). Write side = `android_escape`
  (`' → \'`, `"` → `\"`, `\` → `\\`, newline → `\n`, tab → `\t`, literal `%` →
  `%%`; placeholders pass through). The round-trip is asserted by `selftest` —
  run it if you touch either function.
- **Manifest stores logical text.** Every stage operates on the decoded text,
  never on raw file text.
- **Placeholders are sacred.** `%1$s`, `%s`, `%d`, etc. must survive MT and
  re-escaping unchanged. `selftest` covers the scanner; `qa` covers the output.
- **`selftest` must pass** before you trust any change to the escape/placeholder
  code: `python3 localization/localize.py selftest`.

## Strings referenced from AndroidManifest.xml — never localize them

A resource referenced from a manifest (`android:label="@string/app_name"` etc.)
cannot vary by configuration (language). Lint fails release builds:

```
Error: Resources referenced from the manifest cannot vary by configuration
(except for version qualifiers, e.g. -v21). Found variation in de, es, fr, it
[ManifestResource]
```

This bit us once: `app_name` (referenced five times from
`palace-app-palace/src/main/AndroidManifest.xml`) was extracted, translated,
and written to `values-<lang>/`, which made `lintVitalRelease` fatal.

The pipeline now enforces the rule mechanically, no human step required:

1. `extract` scans **every** `AndroidManifest.xml` in the repo for
   `@string/NAME` references (`manifest_referenced_names()`) and marks those
   names with `skip: true, reason: "manifest-referenced"`. The name set is
   recorded in the manifest under `"manifest_referenced"`. Exclusion is
   by *name* across all modules: the app module's copy is what the merged
   manifest resolves to (localizing it is the fatal case), and library
   modules' copies of the same name are dead resources (overridden at
   merge) — localizing them is worthless.
2. `translate`, `qa`, and `write` all honor the skip flag; `merged_translations`
   additionally drops skipped names as a defense against stale translations
   files that predate the flag.
3. `write` deletes a `values-<lang>` file when its current entry count drops
   to zero (stale-output guard) — a file must not survive the strings that
   filled it becoming non-localizable.
4. `selftest` asserts the scanner finds `app_name` in this repo.

**If you ever see the `ManifestResource` lint error again:** a manifest
reference changed or the manifest is stale. Re-run `extract` (the guard
re-scans the manifests automatically), then `write <lang>` + `verify <lang>`
for each language. Do not hand-edit the `values-<lang>` tree to fix it — the
next `write` would resurrect the stale state.

## Optional: back-translation divergence gate

To push confidence beyond the manual domain audit, add a gate that translates
each target string back to English and flags those whose back-translation
diverges from the source. This catches domain/context errors automatically (it
would have caught most of `hold`→`Laderäume`, `feed`→`food`, etc.) and needs
the reverse model pairs (`de_en es_en fr_en it_en`). It is a heuristic (MT is
lossy), so treat large-divergence flags as "review," not "error." Not part of
the default run.

## Open decisions (ask the user before standardizing)

(none — register/person is decided and mechanically policed; see the glossary)

## Files produced

| Path | Meaning |
|---|---|
| `localization/out/manifest.json` | extracted source (logical text) |
| `localization/out/translations/<lang>.json` | raw MT output |
| `localization/out/qa/<lang>.json` | QA report (errors + reviews) |
| `localization/overrides/<lang>.json` | agent corrections (win over MT; **versioned**) |

`out/` holds only regenerable artifacts (`manifest.json` via `extract`,
`translations/` via `translate`, `qa/` via `qa`) and is gitignored wholesale;
`localization/overrides/` is versioned separately.
| `<module>/src/main/res/values-<lang>/*.xml` | **final deliverables** |
