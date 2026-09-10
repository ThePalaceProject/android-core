#!/usr/bin/env python3
"""
Localize: extract, translate, QA, and write back Android string resources.

Design
------
* No regular expressions anywhere. All text handling is explicit character
  scanning.
* Read path decodes Android escapes (aapt2_decode); write path re-encodes them
  (android_escape). The model is:

      aapt2_decode(raw source) -> logical English -> MT -> logical target
                                                            |
      aapt2 file  <- android_escape <----------------------+

  aapt2_decode inverts aapt2's read-side rules: escape sequences (\' \" \\
  \\ \\n \\t \\uXXXX, %%), whitespace-run folding (layout newlines/spaces/tabs
  collapse to a single space; intended \\n/\\t are shielded first), and the
  aapt2 quote toggle (a leading and trailing unescaped " pair is stripped).
  The manifest therefore stores the *logical* (display) text, and every
  downstream stage (translate, qa, write) operates on logical text. Without the
  read-side decode the pipeline double-escapes source escapes (\' -> \\\' ->
  aapt2 renders a literal backslash).
* Correctness is enforced by a verify-and-fix loop, not by perfect
  transformation. `qa` flags objective breakage (missing, empty, untranslated,
  placeholder mismatch). Generic MT has no domain glossary, so it also
  mistranslates the library vocabulary (hold/loan/patron/feed/...); the `domain`
  command lists those strings and GLOSSARY.md holds the correct terms, and a
  human-free fixer (the agent) corrects both the QA-flagged and the domain
  errors into overrides/LANG.json (localization/overrides/, outside out/).
  `qa` is re-run until clean, then `write`
  emits the resources.

Commands
--------
  extract                 -> localization/out/manifest.json
  translate LANG          -> localization/out/translations/LANG.json
  qa LANG                 -> localization/out/qa/LANG.json (+ stdout report)
  domain [LANG ...]       -> prints domain-sensitive strings + current
                             translations for the MT domain audit/fix pass
  write LANG              -> <module>/src/main/res/values-LANG/*.xml
  selftest                -> internal assertions on escape/placeholder logic

Environment / flags
-------------------
  LIBRETRANSLATE_URL      base url of the self-hosted server (default
                          http://127.0.0.1:5000); overridable per-run with
                          --base-url.  --batch sets the request chunk size.
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
from pathlib import Path

# This script lives in <repo>/localization/; the Android sources are at the repo root.
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "localization" / "out"
# Overrides are the non-regenerable correction layer; they live OUTSIDE out/
# so that out/ (regenerable) can be gitignored wholesale. See RUNBOOK.md.
OVERRIDES = ROOT / "localization" / "overrides"
SRC_LANG = "en"
DEFAULT_BASE = os.environ.get("LIBRETRANSLATE_URL", "http://127.0.0.1:5000")

# Library-domain terms used by the `domain` audit command. Extend this list --
# and GLOSSARY.md -- whenever a new domain mistranslation is found, so future
# runs catch the same class of error.
DOMAIN_TERMS = [
    # library-domain terms
    "hold", "borrow", "loan", "patron", "due", "reserv",
    "feed", "librar", "shelf", "card", "audiobook", "e-book",
    "ebook", "member", "checkout", "opds",
    # common UI words Argos systematically mistranslates (see glossary.md,
    # section "Common UI words"): the audit must surface these strings too
    "minute", "second", "play", "back", "remaining", "rewind",
    "forward", "cover", "sync", "search",
]

# Values that are CORRECT when identical to the source (brands, acronyms,
# units, numbers, and words identical in source and target). qa suppresses
# its "untranslated" review flag for these and reports only the unexpected
# ones. Keep in sync with glossary.md ("Leave verbatim").
VERBATIM_OK = {
    "OK", "FAQ", "EPUB", "PDF", "Palace", "Crash", "Crashlytics",
    "simplified-viewer-epub-readium2", "00:00:00", "30",
    "d", "h", "m", "w", "y",
    "Information", "Format", "Filter (%1$d)", "Passphrase", "Pause", "Minute",
    "Documentation", "Notifications", "1 minute", "Minutes",
    # programmatic notification-channel ids: must stay identical to source
    "channel_id_loans", "channel_id_reservations",
}

# Words that may legitimately remain in source form in a correct translation
# (brands, acronyms, technical ids, established loanwords). Used by the
# english-left-in heuristic to decide what "surviving source words" means.
# All lowercase; compared against lowercased letter-runs. Keep in sync with
# glossary.md ("Leave verbatim" / "Acceptable loanwords").
VERBATIM_FILTER = {
    w.lower() for w in VERBATIM_OK
} | {
    # brands / protocols / acronyms / technical
    "download", "player", "ebook", "e-book", "wifi", "app", "feed", "android",
    "readium", "adobe", "acs", "open ebooks", "opds", "lcp", "drm", "saml",
    "oidc", "openid", "connect", "sso", "web", "uri", "url", "id", "qa",
    "eula", "database",
    # established loanwords in the target languages
    "debug", "analytics", "support", "offline", "details", "provider",
    "account", "card", "creator", "time", "tracking", "audio", "engine",
    "channel", "options", "sync", "privacy", "report", "core", "checkout",
    "drawer", "minutes", "seconds", "hour", "minute", "second",
    "software", "directory", "cache", "openebooks", "table",
    # cognates / loanwords identical or near-identical in the target languages
    "manual", "description", "position", "mode", "password",
    # 'button' is a standard German loanword (a11y labels: "X-Button")
    "button",
    # place names (proper nouns)
    "new", "york",
}

# Known Argos error signatures per language (the "typically gets wrong" column
# of glossary.md, made mechanical). Any effective value containing one is
# flagged for review. Single words are whole-word (case-insensitive);
# multi-word phrases are plain substring. Add new signatures here AND to the
# glossary when the domain audit finds them.
BAD_TERMS = {
    "de": ["Darlehen", "Kredit", "Kreditlimit", "rückzahlbar", "Vorbehalte",
           "Laderäume", "Patron", "Patrone", "Futter", "Beladefutter",
           "Anleihen", "Platzierung", "auf Eis gelegt",
           "Protokoll", "Rücken", "Zweite", "bleibt bestehen",
           "Spielmuster"],
    "es": ["reembolsable", "bodega", "sujeción", "alimentación", "alimento",
           "pienso", "patrón", "socio", "cajero", "marca registrada",
           "Jugar", "Permaneciendo", "Saltear", "fallado"],
    "fr": ["remboursable", "tiroir de caisse", "cale", "Prises", "Réserves",
           "Jouer", "Procès-verbal", "Deuxièmes", "Deuxième"],
    "it": ["Bordo", "Finanziamento", "libreria", "librerie", "mangimi",
           "Denominazione", "patrono", "parso", "Cancelliere", "sportello",
           "Gioca", "Giocato", "Processo verbale", "Orario", "gioco"],
}

# --------------------------------------------------------------------------
# Placeholder handling (character scan, no regex)
# --------------------------------------------------------------------------
# Recognized conversion specifiers: s, d, f.  Positional form is %N$<conv>,
# non-positional is %<conv>.  A literal percent is %% (or a bare % not followed
# by a recognized specifier).  Date/time specifiers (%1$tX) are NOT in this
# corpus and are intentionally not recognized; if one appears, extend here.

_CONV = set("sdf")


def placeholder_len(s: str, i: int) -> int:
    """If s[i] == '%' begins a recognized placeholder, return its length, else 0."""
    n = len(s)
    if i + 1 >= n:
        return 0
    c1 = s[i + 1]
    if c1 == '%':            # '%%' literal percent
        return 0
    # positional: %<digits>$<conv>
    k = i + 1
    while k < n and s[k].isdigit():
        k += 1
    if k > i + 1 and k < n and s[k] == '$':
        conv = k + 1
        if conv < n and s[conv] in _CONV:
            return conv - i + 1
        return 0
    # non-positional: %<conv>
    if c1 in _CONV:
        return 2
    return 0


def placeholder_spans(s: str):
    """Return [(start, end, token), ...] for every placeholder, in order.

    Spans let us splice in sentinel tokens (protect) and restore them later,
    with no regular expressions.
    """
    spans = []
    i, n = 0, len(s)
    while i < n:
        if s[i] == '%':
            L = placeholder_len(s, i)
            if L:
                spans.append((i, i + L, s[i:i + L]))
                i += L
                continue
        i += 1
    return spans


def placeholders(s: str):
    """Return (positional, nonpositional) as ordered lists of the literal tokens."""
    pos, nonpos = [], []
    for _, _, tok in placeholder_spans(s):
        (pos if '$' in tok else nonpos).append(tok)
    return pos, nonpos


def protect_placeholders(text: str):
    """Replace each placeholder with a sentinel (X0X, X1X, ...) that MT copies
    verbatim (empirically verified against this Argos build). Returns
    (protected_text, [(sentinel, original_token), ...]).

    Argos mangles '%1$s' (inserts spaces, drops the trailing 's'); a short
    letter-digit-letter token survives, so we shield the real token behind it.
    """
    spans = placeholder_spans(text)
    if not spans:
        return text, []
    chars = list(text)
    for idx in range(len(spans) - 1, -1, -1):     # splice right-to-left
        a, b, _tok = spans[idx]
        chars[a:b] = list(f"X{idx}X")
    return "".join(chars), [(f"X{idx}X", spans[idx][2]) for idx in range(len(spans))]


def restore_placeholders(text: str, mapping):
    for sent, tok in mapping:
        text = text.replace(sent, tok)
    return text


# --------------------------------------------------------------------------
# Android write-back escaping (logical text -> file text), character scan
# --------------------------------------------------------------------------
def android_escape(s: str) -> str:
    """Escape a *plain* translated string for an unquoted Android <string>.

    Placeholders pass through verbatim; a lone % becomes %%; apostrophes,
    double quotes, backslashes, newlines and tabs are backslash-escaped.  The
    result is the element *text*; XML escaping of & < > is done by ElementTree
    at serialization time.
    """
    out = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == '%':
            L = placeholder_len(s, i)
            if L:
                out.append(s[i:i + L])
                i += L
                continue
            out.append('%%')
            i += 1
            continue
        if c == "'":
            out.append("\\'")
        elif c == '"':
            out.append('\\"')
        elif c == '\\':
            out.append('\\\\')
        elif c == '\n':
            out.append('\\n')
        elif c == '\t':
            out.append('\\t')
        else:
            out.append(c)
        i += 1
    return "".join(out)


# Android resource decoder (file text -> logical text), the inverse of
# android_escape plus aapt2's whitespace/quote handling.  Applied on *read* so
# the manifest holds the logical string (what aapt2 renders), which is what we
# translate.  aapt2 trims/folds layout whitespace, decodes backslash escapes,
# and strips one outer pair of unescaped double-quotes (the quote toggle).
def aapt2_decode(s: str) -> str:
    v = s
    # quote toggle: strip one outer pair of unescaped double-quotes
    if len(v) >= 2 and v[0] == '"' and v[-1] == '"' and v.count('"') % 2 == 0:
        v = v[1:-1]
    NL = "\x00NL\x00"
    TAB = "\x00TAB\x00"
    out = []
    pending_ws = False

    def push(ch):
        nonlocal pending_ws
        if ch in " \t\n\r":
            if not pending_ws:
                out.append(" ")
            pending_ws = True
        else:
            out.append(ch)
            pending_ws = False

    i, n = 0, len(v)
    while i < n:
        c = v[i]
        if c == '\\' and i + 1 < n:
            d = v[i + 1]
            if d == "n":
                push(NL); i += 2; continue
            if d == "t":
                push(TAB); i += 2; continue
            if d == "u":
                hx = v[i + 2:i + 6]
                if len(hx) == 4 and all(k in "0123456789abcdefABCDEF" for k in hx):
                    push(chr(int(hx, 16))); i += 6; continue
            if d == "\\":
                push("\\"); i += 2; continue
            if d == "'":
                push("'"); i += 2; continue
            if d == '"':
                push('"'); i += 2; continue
            if d == "%":
                push("%"); i += 2; continue
            push(c); i += 1; continue
        if c == '%' and i + 1 < n and v[i + 1] == '%':
            push('%'); i += 2; continue
        push(c)
        i += 1
    text = "".join(out).strip()
    text = text.replace(NL, "\n").replace(TAB, "\t")
    return text


# --------------------------------------------------------------------------
# Classification helpers (no regex)
# --------------------------------------------------------------------------
def is_reference(v: str) -> bool:
    return v.lstrip().startswith("@")


def is_lorem(v: str) -> bool:
    return "lorem ipsum" in v.lower()


def is_numeric_only(v: str) -> bool:
    return v.strip() != "" and v.strip().isdigit()


# --------------------------------------------------------------------------
# File discovery (mirrors string-files.sh)
# --------------------------------------------------------------------------
def find_string_files():
    files = []
    for p in ROOT.rglob("*.xml"):
        if "build" in p.parts:
            continue
        # Only the default (English) values dir, not localized values-XX/ dirs.
        if p.parent.name == "values" and "string" in p.name:
            files.append(p)
    return sorted(files)


def module_of(f: Path) -> str:
    """Gradle module root = everything above the 'src' segment of the res path."""
    parts = f.relative_to(ROOT).parts
    return "/".join(parts[:parts.index("src")])


# --------------------------------------------------------------------------
# extract
# --------------------------------------------------------------------------
def manifest_referenced_names():
    """String names referenced from any AndroidManifest.xml in the repo.

    A resource referenced from a manifest cannot vary by configuration
    (language) -- lint fails release builds with a fatal ManifestResource
    error ("Resources referenced from the manifest cannot vary by
    configuration"). Such names are excluded from localization entirely:
    the app module's copy is what the manifest resolves to, and library
    modules' copies of the same name are dead (overridden at merge).
    """
    names = set()
    needle = "@string/"
    for f in ROOT.rglob("AndroidManifest.xml"):
        if "build" in f.parts:
            continue
        text = f.read_text(encoding="utf-8")
        i = text.find(needle)
        while i != -1:
            j = i + len(needle)
            k = j
            while k < len(text) and (text[k].isalnum() or text[k] == "_"):
                k += 1
            if k > j:
                names.add(text[j:k])
            i = text.find(needle, j)
    return names


def extract():
    manref = manifest_referenced_names()
    modules = []
    for f in find_string_files():
        root = ET.parse(f).getroot()
        entries = []
        for el in root.iter():
            if el.tag == "string":
                name = el.get("name")
                if name is None:
                    continue
                raw = aapt2_decode(el.text or "")
                pos, nonpos = placeholders(raw)
                ref = is_reference(raw)
                lorem = is_lorem(raw)
                manref_hit = name in manref
                entries.append({
                    "name": name, "type": "string", "value": raw,
                    "positional": pos, "nonpositional": nonpos,
                    "skip": ref or lorem or manref_hit,
                    "reason": ("reference" if ref else
                               "lorem" if lorem else
                               ("manifest-referenced" if manref_hit else None)),
                })
            elif el.tag == "string-array":
                name = el.get("name")
                if name is None:
                    continue
                items = [aapt2_decode(it.text or "") for it in el if it.tag == "item"]
                numeric = bool(items) and all(is_numeric_only(x) for x in items)
                entries.append({
                    "name": name, "type": "array", "items": items,
                    "positional": [], "nonpositional": [],
                    "skip": numeric,
                    "reason": "numeric-values" if numeric else None,
                })
        modules.append({
            "module": module_of(f),
            "path": f.relative_to(ROOT).as_posix(),
            "entries": entries,
        })

    all_entries = [e for m in modules for e in m["entries"]]
    all_str = [e for e in all_entries if e["type"] == "string"]
    summary = {
        "string_files": len(modules),
        "strings": len(all_str),
        "arrays": len(all_entries) - len(all_str),
        "translatable_strings": sum(1 for e in all_str if not e["skip"]),
        "skipped_strings": sum(1 for e in all_str if e["skip"]),
        "with_placeholders": sum(1 for e in all_str if e["positional"] or e["nonpositional"]),
        "with_newline": sum(1 for e in all_str if "\n" in e["value"]),
        "skips": _count_reasons(all_entries),
    }
    return {"source_language": SRC_LANG, "generated_by": "localize.py extract",
            "modules": modules, "summary": summary,
            "manifest_referenced": sorted(manref)}


def _count_reasons(entries):
    c = {}
    for e in entries:
        if e["skip"]:
            c[e["reason"]] = c.get(e["reason"], 0) + 1
    return c


def dump(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


def load_manifest():
    return json.loads((OUT / "manifest.json").read_text(encoding="utf-8"))


def load_translations(lang: str):
    p = OUT / "translations" / f"{lang}.json"
    return json.loads(p.read_text(encoding="utf-8"))


def merged_translations(lang: str):
    """MT output with curated overrides applied on top.

    Overrides live at localization/overrides/<lang>.json with the same
    nested shape (path -> name -> value). They make the fix pass idempotent:
    re-running translate regenerates MT output, but curated corrections persist
    and always win. qa and write both read this merged view.
    """
    # Never merge a string the manifest marks as untranslatable -- in
    # particular a name referenced from AndroidManifest.xml cannot vary by
    # language (lint ManifestResource, fatal on release). This also guards
    # against stale translations files that predate the skip flag.
    skip_names = {e["name"] for m in load_manifest()["modules"]
                  for e in m["entries"] if e.get("skip")}
    merged = {p: {n: v for n, v in d.items() if n not in skip_names}
              for p, d in load_translations(lang)["translations"].items()}
    opath = OVERRIDES / f"{lang}.json"
    if opath.exists():
        ov = json.loads(opath.read_text(encoding="utf-8"))
        for path, d in ov.items():
            for name, val in d.items():
                merged.setdefault(path, {})[name] = val
    return merged


def override_problems(lang):
    """Misplaced/stale override keys. An override only takes effect if its path
    matches the manifest path of the named string; anything else is silently
    dropped by write. Returns human-readable problems (empty = clean)."""
    op = OVERRIDES / f"{lang}.json"
    if not op.exists():
        return []
    ov = json.loads(op.read_text(encoding="utf-8"))
    true_path = {}
    for mod in load_manifest()["modules"]:
        for e in mod["entries"]:
            true_path[e["name"]] = mod["path"]
    probs = []
    for path, d in ov.items():
        for name in d:
            if true_path.get(name) is None:
                probs.append(f"override key {name} under {path} matches no "
                             f"manifest string (no effect; stale?)")
            elif true_path[name] != path:
                probs.append(f"override key {name} under {path} but the string "
                             f"lives in {true_path[name]} (no effect; re-key it)")
    return probs


# --------------------------------------------------------------------------
# translate (self-hosted LibreTranslate / Argos REST API)
# --------------------------------------------------------------------------
def http_post_json(url: str, payload: dict, retries: int = 4, timeout: int = 120):
    data = json.dumps(payload).encode("utf-8")
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(
                url, data=data, headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            last = f"HTTP {e.code}: {body[:300]}"
            if 400 <= e.code < 500 and e.code not in (413, 429):
                raise RuntimeError(f"MT client error (not retried): {last}")
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError) as e:
            last = repr(e)
        time.sleep(1 + attempt)
    raise RuntimeError(f"MT request to {url} failed after {retries} tries: {last}")


def _extract_translations(resp):
    """Normalize the MT response to a list of translated strings.

    This LibreTranslate build returns:
      * single q  -> {"translatedText": "..."}
      * list q    -> {"translatedText": ["...", ...]}   (array under the same key)
    Older/other builds use {"translations": [{"translatedText": ...}, ...]}.
    All three are handled.
    """
    if "translations" in resp:
        return [x.get("translatedText", "") if isinstance(x, dict) else x
                for x in resp["translations"]]
    tt = resp.get("translatedText")
    if isinstance(tt, list):
        return tt
    if isinstance(tt, str):
        return [tt]
    raise RuntimeError(f"unexpected MT response: {str(resp)[:300]}")


def mt_translate(texts, source, target, base_url, batch):
    out = []
    for i in range(0, len(texts), batch):
        chunk = texts[i:i + batch]
        resp = http_post_json(base_url.rstrip("/") + "/translate",
                              {"q": chunk, "source": source, "target": target, "format": "text"})
        t = _extract_translations(resp)
        if len(t) != len(chunk):
            raise RuntimeError(f"MT returned {len(t)} results for {len(chunk)} inputs")
        out.extend(t)
    return out


def translate(lang: str, base_url: str, batch: int):
    manifest = load_manifest()
    jobs = []  # (path, name, kind, raw_text)
    for m in manifest["modules"]:
        p = m["path"]
        for e in m["entries"]:
            if e["skip"]:
                continue
            if e["type"] == "string":
                jobs.append((p, e["name"], "string", e["value"]))
            else:
                for it in e["items"]:
                    jobs.append((p, e["name"], "array", it))

    # Shield placeholders behind MT-proof sentinels, translate, restore.
    protected, maps = [], []
    for (_p, _name, _kind, raw) in jobs:
        prot, mapp = protect_placeholders(raw)
        protected.append(prot)
        maps.append(mapp)
    raw_results = mt_translate(protected, SRC_LANG, lang, base_url, batch)
    results = [restore_placeholders(tr, mapp) for tr, mapp in zip(raw_results, maps)]

    # Key by source-file path, then name: names are unique within a file but
    # repeat across modules (app_name, Dismiss), so a flat name-key is unsafe.
    translations = {}
    for (p, name, kind, _), tr in zip(jobs, results):
        d = translations.setdefault(p, {})
        if kind == "array":
            d.setdefault(name, []).append(tr)
        else:
            d[name] = tr

    out = dump(OUT / "translations" / f"{lang}.json",
               {"lang": lang, "source_language": SRC_LANG, "base_url": base_url,
                "count": sum(len(v) if isinstance(v, list) else 1
                             for d in translations.values() for v in d.values()),
                "translations": translations})
    print(f"wrote {out}  ({len(jobs)} units translated)")
    return out


# --------------------------------------------------------------------------
# qa
# --------------------------------------------------------------------------
def _check_unit(src: str, tr: str):
    """Return list of (severity, reason) for one source/translation pair."""
    src = "" if src is None else str(src)
    if not src.strip():
        # Empty source has nothing to translate; an empty translation is correct.
        return []
    if tr is None or not str(tr).strip():
        return [("error", "empty")]
    tr = str(tr)
    if tr.strip() == src.strip():
        # review, not error: could be a legitimate identical term (acronym/brand)
        return [("review", "untranslated (== source)")]
    sp, sn = placeholders(src)
    tp, tn = placeholders(tr)
    reasons = []
    if sorted(sp) != sorted(tp):
        reasons.append(("error", f"positional mismatch  src={sp}  tr={tp}"))
    if sn != tn:
        reasons.append(("error", f"nonpositional mismatch  src={sn}  tr={tn}"))
    return reasons


def _content_words(s, filt):
    """Letter-runs of length > 2, lowercased, minus known verbatim/loanwords.
    (No regex: plain character scan.)"""
    words, cur = [], []
    for ch in s:
        if ch.isalpha():
            cur.append(ch)
        else:
            if cur:
                words.append("".join(cur))
                cur = []
    if cur:
        words.append("".join(cur))
    return [w.lower() for w in words if len(w) > 2 and w.lower() not in filt]


def _english_left_in(src, tr):
    """Suspect partially-untranslated output: half or more of the source's
    content words (min 2) survive in the translation."""
    sw = set(_content_words(src, VERBATIM_FILTER))
    if len(sw) < 2:
        return False
    hits = sum(1 for w in sw if _word_hit(tr, w))
    return hits * 2 >= len(sw)


def _bad_term_hit(tr, lang):
    for t in BAD_TERMS.get(lang, []):
        if " " in t:
            if t.lower() in tr.lower():
                return t
        elif _word_hit(tr, t):
            return t
    return None


# Formal register (DECISION, glossary.md: formal tone everywhere -- de Sie,
# fr vous, es usted, it Lei). These lists contain the INFORMAL 2nd-person
# forms that are unambiguous, i.e. they differ from the 3rd-person singular.
# For regular -AR verbs the es/it tú-imperative is identical to the
# 3rd-person present ("Mostra"/"Cierra"), so those cannot be flagged without
# false positives; they are checked by eye in the domain audit (glossary.md).
INFORMAL_WORDS = {
    "de": {"du", "dich", "dir", "euch", "wirst", "kommst", "machst",
           "siehst", "tippst", "drückst", "öffnest", "wählst", "suchst",
           "gibst", "nimmst", "legst", "stellst", "holst", "lass", "sieh",
           "wähl", "hörst", "liest", "kaufst"},
    "es": {"tú", "tu", "tus", "estás", "has", "tienes", "quieres", "puedes",
           "debes", "sabes", "deseas", "necesitas", "haces", "ves", "vuelves",
           "haz", "ten", "ven"},
    "fr": {"tu", "ton", "ta", "tes", "écris", "reviens", "choisis"},
    "it": {"tu", "tuo", "tua", "tuoi", "tue", "sei", "hai", "vuoi", "puoi",
           "devi", "stai", "vai", "fai", "dici", "vedi", "metti", "prendi",
           "scrivi", "chiudi", "apri", "aggiungi", "rimuovi", "scegli",
           "accedi", "inserisci", "nascondi", "riavvolgi", "leggi", "premi",
           "riprendi", "esegui", "riempi", "condividi", "richiedi", "spegni",
           "attendi", "tieni", "prenoti"},
}

def _register_hit(tr, lang):
    for w in INFORMAL_WORDS.get(lang, ()):
        if _word_hit(tr, w):
            return f"informal register '{w}' (decision: formal)"
    return None


def qa(lang: str):
    manifest = load_manifest()
    all_trans = merged_translations(lang)
    flags = []

    def add(module, name, kind, idx, src, tr, sev, reason):
        flags.append({"module": module, "name": name, "kind": kind, "index": idx,
                      "source": src, "translation": tr, "severity": sev, "reason": reason})

    # Hard gate: misplaced/stale override keys are a silent-corruption class.
    for p in override_problems(lang):
        flags.append({"module": "(overrides)", "name": "override file", "kind": "override",
                      "index": None, "source": None, "translation": None,
                      "severity": "error", "reason": p})

    def content_checks(mod, name, kind, idx, src, tr):
        # double-dot: no source string contains '..', so any in a value is the
        # ellipsis artifact (.. instead of …)
        if ".." in tr:
            add(mod, name, kind, idx, src, tr, "error", "double-dot '..' (use …)")
        bt = _bad_term_hit(tr, lang)
        if bt is not None:
            add(mod, name, kind, idx, src, tr, "review", f"known-bad term {bt!r} (glossary signature)")
        # (identical values are the untranslated/verbatim case -- the
        #  english-left-in heuristic must not double-report them)
        if tr.strip() != src.strip() and _english_left_in(src, tr):
            add(mod, name, kind, idx, src, tr, "review", "english left in (most source words survive)")
        rg = _register_hit(tr, lang)
        if rg is not None:
            add(mod, name, kind, idx, src, tr, "review", rg)

    for m in manifest["modules"]:
        mod = m["module"]
        tmap = all_trans.get(m["path"], {})
        for e in m["entries"]:
            if e["skip"]:
                continue
            name = e["name"]
            if e["type"] == "string":
                if name not in tmap:
                    add(mod, name, "string", None, e["value"], None, "error", "missing translation")
                    continue
                tr = tmap[name]
                for sev, reason in _check_unit(e["value"], tr):
                    add(mod, name, "string", None, e["value"], tr, sev, reason)
                if tr is not None and str(tr).strip():
                    content_checks(mod, name, "string", None, e["value"], str(tr))
            else:  # array
                if name not in tmap:
                    add(mod, name, "array", None, e["items"], None, "error", "missing translation")
                    continue
                tr_items = tmap[name]
                if len(tr_items) != len(e["items"]):
                    add(mod, name, "array", None, e["items"], tr_items, "error",
                        f"item count mismatch  src={len(e['items'])}  tr={len(tr_items)}")
                    continue
                for idx, (s_it, t_it) in enumerate(zip(e["items"], tr_items)):
                    for sev, reason in _check_unit(s_it, t_it):
                        add(mod, name, "array", idx, s_it, t_it, sev, reason)
                    if t_it is not None and str(t_it).strip():
                        content_checks(mod, name, "array", idx, s_it, str(t_it))

    errors = [f for f in flags if f["severity"] == "error"]
    # Suppress 'untranslated (== source)' reviews whose value is a known-good
    # verbatim (VERBATIM_OK); report only the unexpected ones.
    def is_known_verbatim(f):
        return (f["severity"] == "review"
                and f["reason"].startswith("untranslated")
                and f["translation"] in VERBATIM_OK)
    known = [f for f in flags if is_known_verbatim(f)]
    reviews = [f for f in flags if f["severity"] == "review" and not is_known_verbatim(f)]
    report = {"lang": lang, "errors": len(errors), "review": len(reviews),
              "known_verbatim": len(known), "flags": flags, "clean": len(errors) == 0}
    dump(OUT / "qa" / f"{lang}.json", report)

    print(f"qa {lang}: {len(errors)} error(s), {len(reviews)} review(s) "
          f"({len(known)} known verbatim suppressed)")
    for f in errors:
        loc = f["name"] + (f"[{f['index']}]" if f["index"] is not None else "")
        print(f"  ERROR [{f['module']}] {loc}: {f['reason']}")
        print(f"        src={f['source']!r}")
        print(f"        tr ={f['translation']!r}")
    if reviews:
        print(f"  review (each needs a disposition: fix it, or add to "
              f"VERBATIM_OK/BAD_TERMS + glossary):")
        for f in reviews:
            loc = f["name"] + (f"[{f['index']}]" if f["index"] is not None else "")
            print(f"    - [{f['module']}] {loc}: {f['source']!r}")
    return report


# --------------------------------------------------------------------------
# write
# --------------------------------------------------------------------------
def write(lang: str):
    probs = override_problems(lang)
    if probs:
        print(f"write {lang}: REFUSED -- {len(probs)} misplaced/stale override key(s):")
        for p in probs:
            print(f"  {p}")
        sys.exit(1)
    manifest = load_manifest()
    all_trans = merged_translations(lang)
    written = []
    for m in manifest["modules"]:
        tmap = all_trans.get(m["path"], {})
        src = ROOT / m["path"]                       # .../<mod>/src/main/res/values/<base>
        resdir = src.parent.parent                   # .../<mod>/src/main/res
        outdir = resdir / f"values-{lang}"           # .../<mod>/src/main/res/values-<lang>
        res = ET.Element("resources")
        count = 0
        for e in m["entries"]:
            if e["skip"]:
                continue
            name = e["name"]
            if e["type"] == "string":
                if name not in tmap:
                    continue
                el = ET.SubElement(res, "string", {"name": name})
                el.text = android_escape(tmap[name])
                count += 1
            else:
                if name not in tmap:
                    continue
                arr = ET.SubElement(res, "string-array", {"name": name})
                for it in tmap[name]:
                    item = ET.SubElement(arr, "item")
                    item.text = android_escape(it)
                count += 1
        outpath = outdir / src.name
        if count == 0:
            # Stale-output guard: a previous run may have written this file
            # when its entries were still translatable (e.g. a string later
            # became manifest-referenced). The values-<lang> tree is
            # pipeline-generated, so a file with zero current entries must
            # not survive -- delete it.
            if outpath.exists():
                outpath.unlink()
                print(f"  removed stale {outpath.relative_to(ROOT)}")
            continue
        outdir.mkdir(parents=True, exist_ok=True)
        ET.indent(res, space="    ")
        body = ET.tostring(res, encoding="unicode")
        outpath.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + body + "\n",
                           encoding="utf-8")
        written.append(outpath)

    # verify well-formedness by re-parsing every file we emitted
    for p in written:
        ET.parse(p)
    print(f"wrote {len(written)} resource file(s) for {lang}; all re-parsed OK")
    for p in written:
        print(f"  {p.relative_to(ROOT)}")
    return written


# --------------------------------------------------------------------------
# verify (post-write gate)
# --------------------------------------------------------------------------
def verify(lang: str):
    """Re-parse every written values-<lang> file and assert each value equals
    the merged translation after aapt2-decode. Catches writer drift, dropped
    or mis-keyed overrides (the written file would lack them), and artifact
    sequences qa missed. Exits 1 on any problem."""
    manifest = load_manifest()
    merged = merged_translations(lang)
    problems = []
    checked = 0
    for m in manifest["modules"]:
        if all(e["skip"] for e in m["entries"]):
            continue
        src = ROOT / m["path"]
        outpath = src.parent.parent / f"values-{lang}" / src.name
        if not outpath.exists():
            problems.append(f"missing written file: {outpath.relative_to(ROOT)}")
            continue
        root = ET.parse(outpath).getroot()
        got = {}
        for el in root:
            if el.tag == "string":
                got[el.get("name")] = aapt2_decode(el.text or "")
            elif el.tag == "string-array":
                got[el.get("name")] = [aapt2_decode(i.text or "") for i in el]
        for e in m["entries"]:
            if e["skip"]:
                continue
            name = e["name"]
            expect = merged.get(m["path"], {}).get(name)
            have = got.get(name)
            if expect is None and have is not None:
                problems.append(f"{m['path']}::{name} written but has no translation")
            elif expect is not None and have != expect:
                problems.append(f"{m['path']}::{name} drift\n  expected={expect!r}\n  written ={have!r}")
            if have is not None:
                checked += 1
                val = have if isinstance(have, str) else " ".join(have)
                if ".." in val:
                    problems.append(f"{m['path']}::{name} contains '..' (ellipsis artifact)")
    if problems:
        print(f"verify {lang}: {len(problems)} problem(s)")
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print(f"verify {lang}: {checked} entries checked; written files match merged translations; no artifacts")


# --------------------------------------------------------------------------
# domain audit (for the MT domain-fix pass)
# --------------------------------------------------------------------------
def _word_hit(text: str, term: str) -> bool:
    """Case-insensitive whole-word containment of `term` in `text` (no regex)."""
    t = text.lower(); tl = term.lower(); n = len(tl)
    i = t.find(tl)
    while i != -1:
        before_ok = i == 0 or not t[i - 1].isalnum()
        end = i + n
        after_ok = end >= len(t) or not t[end].isalnum()
        if before_ok and after_ok:
            return True
        i = t.find(tl, i + 1)
    return False


def domain(langs=None):
    """Print every source string containing a library-domain term, with the
    current merged translation per language, so the agent can audit and fix
    domain-specific MT errors (see GLOSSARY.md). Already-overridden entries are
    tagged so the agent knows what was already corrected."""
    manifest = load_manifest()
    if not langs:
        langs = ["de", "es", "fr", "it"]
    data = {}
    for l in langs:
        tr = {}
        tp = OUT / f"translations/{l}.json"
        if tp.exists():
            tr = json.loads(tp.read_text()).get("translations", {})
        ov = {}
        op = OVERRIDES / f"{l}.json"
        if op.exists():
            ov = json.loads(op.read_text())
        data[l] = (tr, ov)
    count = 0
    for m in manifest["modules"]:
        mod = m["module"].split("/")[-1]
        for e in m["entries"]:
            if e["skip"] or e["type"] != "string":
                continue
            if not any(_word_hit(e["value"], t) for t in DOMAIN_TERMS):
                continue
            count += 1
            print(f"### {e['name']}  [{mod}]")
            print(f"  EN: {e['value']}")
            for l in langs:
                tr, ov = data[l]
                overridden = e["name"] in ov.get(m["path"], {})
                v = ov.get(m["path"], {}).get(e["name"])
                if v is None:
                    v = tr.get(m["path"], {}).get(e["name"])
                print(f"  {l}: {v}" + ("   [override]" if overridden else ""))
            print()
    print(f"--- {count} domain-sensitive string(s); terms: {', '.join(DOMAIN_TERMS)}")


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------
def selftest():
    # placeholder scanner
    assert placeholders("Hi %1$s, you have %2$d items") == (["%1$s", "%2$d"], [])
    assert placeholders("a %s b %d c") == ([], ["%s", "%d"])
    assert placeholders("100% done") == ([], [])
    assert placeholders("wait %% then go") == ([], [])
    assert placeholders("%1$s and %s") == (["%1$s"], ["%s"])
    assert placeholders("no placeholders") == ([], [])
    assert placeholders("%") == ([], [])
    assert placeholders("%1$d") == (["%1$d"], [])

    # protect/restore round-trip (sentinel shielding of placeholders)
    prot, mapp = protect_placeholders("The book \\\'%1$s\\\' and %2$s end")
    assert prot == "The book \\\'X0X\\\' and X1X end", prot
    assert restore_placeholders(prot, mapp) == "The book \\\'%1$s\\\' and %2$s end"
    prot2, mapp2 = protect_placeholders("no placeholders here")
    assert prot2 == "no placeholders here" and mapp2 == []
    s3 = "a %1$s %2$d %3$s b %s c"
    assert restore_placeholders(*protect_placeholders(s3)) == s3
    p4, _ = protect_placeholders("x %s y %d z")
    assert "X0X" in p4 and "X1X" in p4

    # escaping: each must invert exactly under the aapt2 decoder
    cases = [
        "Ouvrir l'application",          # French apostrophe
        "c'est 100% sûr",                 # apostrophe + literal percent + accent
        "Ligne un\nLigne deux",           # newline
        "C:\\chemin\\fichier",            # backslashes
        'il a dit "bonjour"',            # double quotes
        "Tu as %1$s livres",             # positional placeholder
        "Il reste %s et %d",             # non-positional placeholders
        "Mix: a'b\\c\n%d %%%d",          # several at once
        "plain text",
        "trailing percent %",
    ]
    for s in cases:
        enc = android_escape(s)
        dec = aapt2_decode(enc)
        assert dec == s, f"round-trip failed:\n  in ={s!r}\n  enc={enc!r}\n  dec={dec!r}"

    # targeted expected encodings
    assert android_escape("Don't") == "Don\\'t"
    assert android_escape("100%") == "100%%"
    assert android_escape("keep %1$s") == "keep %1$s"
    assert android_escape("a%sb%d") == "a%sb%d"
    assert android_escape("x\ny") == "x\\ny"

    # content checks
    assert _english_left_in("Sync your bookmarks", "Sync your bookmarks")
    assert not _english_left_in("Sync your bookmarks", "Synchronizar marcadores")
    assert not _english_left_in("OK", "OK")                      # < 2 content words
    assert not _english_left_in("Download the ebook", "Télécharger l'ebook")
    assert _bad_term_hit("Meine Darlehen", "de") == "Darlehen"
    assert _bad_term_hit("tiroir de caisse pour les prêts", "fr") == "tiroir de caisse"
    assert _bad_term_hit("le tiroir d'emprunt", "fr") is None
    assert _bad_term_hit("Ausleihe widerrufen", "de") is None
    # Formal register (decision): informal 2nd-person forms are flagged.
    assert _register_hit("Gracias, ¿usted prefiere?", "es") is None
    assert _register_hit("¿Quieres cerrar la sesión?", "es") is not None
    assert _register_hit("Tu sesión ha expirado", "es") is not None
    assert _register_hit("Grazie, preferisci tu?", "it") is not None
    assert _register_hit("Lo fa Lei, per favore", "it") is None
    assert _register_hit("lei non c'è", "it") is None             # lowercase = 'they'
    assert _register_hit("Mostra la password", "it") is None       # 3rd person, neutral
    assert _register_hit("Tuo figlio ha 12 anni", "it") is not None
    assert _register_hit("Klicken Sie hier", "de") is None
    assert _register_hit("Du kannst hier klicken", "de") is not None
    assert _register_hit("Cliquez ici", "fr") is None
    assert _register_hit("Tu peux cliquer ici", "fr") is not None

    # manifest-referenced strings cannot vary by language (lint
    # ManifestResource, fatal on release); the scanner must find them.
    assert "app_name" in manifest_referenced_names()

    print(f"selftest: all assertions passed ({len(cases)} round-trip cases + content checks)")


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def _arg(argv, name, default=None):
    if name in argv:
        i = argv.index(name)
        if i + 1 < len(argv):
            return argv[i + 1]
    return default


def main():
    argv = sys.argv[1:]
    cmd = argv[0] if argv else "extract"

    if cmd == "extract":
        data = extract()
        out = dump(OUT / "manifest.json", data)
        s = data["summary"]
        print(f"wrote {out}")
        print(f"  files={s['string_files']}  strings={s['strings']}  "
              f"arrays={s['arrays']}  translatable={s['translatable_strings']}  "
              f"skipped={s['skipped_strings']}  placeholders={s['with_placeholders']}  "
              f"newlines={s['with_newline']}")
        print(f"  skip reasons: {s['skips']}")
        for m in data["modules"]:
            for e in m["entries"]:
                if e["skip"]:
                    val = e.get("value", e.get("items"))
                    print(f"    SKIP [{m['module']}] {e['name']} ({e['reason']}): {val!r}"
                          if e["type"] == "string" else
                          f"    SKIP [{m['module']}] {e['name']} ({e['reason']}): {val!r}")
        return

    if cmd == "translate":
        if len(argv) < 2:
            sys.exit("usage: translate LANG [--base-url URL] [--batch N]")
        lang = argv[1]
        base = _arg(argv, "--base-url", DEFAULT_BASE)
        batch = int(_arg(argv, "--batch", "20") or 20)
        translate(lang, base, batch)
        return

    if cmd == "qa":
        if len(argv) < 2:
            sys.exit("usage: qa LANG")
        qa(argv[1])
        return

    if cmd == "write":
        if len(argv) < 2:
            sys.exit("usage: write LANG")
        write(argv[1])
        return

    if cmd == "domain":
        domain(argv[1:])
        return

    if cmd == "verify":
        if len(argv) < 2:
            sys.exit("usage: verify LANG")
        verify(argv[1])
        return

    if cmd == "selftest":
        selftest()
        return

    sys.exit(f"unknown command: {cmd}  (extract | translate | qa | domain | write | verify | selftest)")


if __name__ == "__main__":
    main()
