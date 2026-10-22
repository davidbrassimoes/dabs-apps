# Sótão — implementation specification

> **Since this was written** (2026-10-06): the app is **Sótão — studio cards** and lives in
> `davidbrassimoes/dabs-apps` at `products/sotao/`. Two sections are overridden. **§3
> Hosting** is Cloudflare Workers + D1 (`wrangler.jsonc`; `projects/sotao.md`, decision 8). **§12 Theming** is
> replaced by `docs/LOOK.md` (look G, approved at gate 2 on 2026-10-06). Everything else stands as written.

**For:** the agent building this in a fresh Claude Code session.
**From:** a design session with the owner (David / DABS). Everything in here was decided
with him and is settled unless a section says otherwise.
**Visual reference:** a Design canvas of eight screen mockups exists. The owner has the
link. Ask him for it before building any screen — the mockups are the source of truth for
layout, and this document is the source of truth for behaviour.

---

## 1. What this is

A card app for musical inspiration, used in a home recording studio (an attic — *sótão* —
in Portugal). The owner writes short prompt cards about things to try with his gear
("Sample yourself whistling into the toy sampler, pitch it down two octaves"), and the app deals
them back at random when he wants a starting point.

Two ways to draw:

- **Single** — one complete, self-contained idea.
- **Spread** — three partial cards side by side that combine into something new
  (a source + a treatment + a constraint).

Plus a manager for writing and editing cards, a gear inventory, and bulk import from a
YAML file so Claude can generate cards in conversation and the owner can paste them in.

### Who uses it

One person. There is no multi-user story, no sharing, no collaboration. Do not build a
users table, a signup flow, roles, or permissions.

---

## 2. Decisions already made

These were argued through. Implement them as written; do not re-litigate them in code
comments or propose alternatives in your first message.

| Decision | Choice |
|---|---|
| Deployment | Hosted web app, installable as a PWA. Not a desktop app, not LAN-only. |
| Why hosted | He wants to draw cards on his phone while jamming with the laptop off. A LAN app served from the laptop fails exactly then. |
| Auth | One password in an env var. Signed cookie, one-year expiry. No user table. |
| Phone | Full editing, same codebase, responsive. Not read-only. |
| Card kinds | A card can hold **several** kinds. |
| Spread | **Three fixed slots**: source + treatment + constraint. Configurable recipes are phase 2. |
| Favourite | A **marker only**. Filterable. **No effect on draw odds.** |
| Draw odds | Uniform random among eligible cards in phase 1. Weighting is phase 2 and gets its own column. |
| Repeat suppression | The **last 30 drawn cards** are excluded. |
| Where the draw runs | **On the client**, from a locally cached pool. The server only records what was drawn. One code path online and off. |
| Offline | In scope from the start. Drawing works with no network; draw records queue and replay. |
| Themes | Dark **and** light, both designed. Dark is the default. |
| Import format | YAML. Strict, schema-validated, idempotent. |
| Gear | A first-class entity. Cards reference gear by slug. Unknown slugs are a hard error. |
| Always-on gear | The desk, the monitors, headphones and the laptop are **not** in the inventory and never gate a draw. |
| Try-log | Not built. No `triedAt`, no per-card notes. |

### Explicitly out of scope for phase 1

Do not build these, and do not leave half-implementations of them:

- Draw-odds weighting of any kind.
- Configurable spread recipes, or spreads of other than three slots.
- Per-card notes, session journals, "what did you actually do" logging.
- Card images, audio attachments, links.
- Any second user.

---

## 3. Stack

| Concern | Choice | Notes |
|---|---|---|
| Framework | SvelteKit 2 + Svelte 5 (runes) | The owner has SvelteKit experience. One language, one build. |
| Language | TypeScript, `strict: true` | |
| Styling | Tailwind CSS 4 | Theme tokens as CSS custom properties, see §12. |
| ORM | Drizzle | Schema in `src/lib/server/db/schema.ts`. Use `drizzle-kit` migrations, checked into the repo. |
| Database | SQLite (libSQL locally) | Local file in dev (`file:./local.db`), Cloudflare D1 in production (decision 8; first written as Turso). |
| Validation | Zod | One schema module shared by the API and the YAML importer. |
| YAML | `yaml` (eemeli) | Use `parseDocument` so you can report line numbers on errors. |
| Tests | Vitest | See §14 for what must be covered. |
| Hosting | Cloudflare Workers + D1 | `wrangler.jsonc`, `projects/sotao.md` decision 8. First written as Vercel + Turso. |

Do not add a state-management library, a component library, or a UI kit. The screens are
simple and the mockups are hand-built markup.

### Project shape

```
src/
  lib/
    draw.ts               the randomiser — PURE, runs on the client
    cardId.ts             text normalisation + hashing — pure, both sides
    pool.ts               IndexedDB pool cache + refresh
    outbox.ts             queued draw records + replay
    schemas.ts            Zod schemas shared by API and importer
    theme.ts              theme token names + the switcher
    server/
      db/schema.ts        Drizzle schema
      db/index.ts         client + migration runner
      auth.ts             cookie sign/verify
      import.ts           YAML parse → validate → diff
      export.ts           db → YAML
  routes/
    +layout.svelte        shell, theme, nav
    draw/+page.svelte     both modes, ?mode=single|spread
    cards/                manager + editor
    gear/                 inventory
    import/               paste/upload, preview, commit
    login/
  hooks.server.ts         auth guard
static/
  manifest.webmanifest
seed/
  gear.ts                 the inventory below, as data
  cards.yaml              starter cards (§15)
```

`src/lib/draw.ts` must be **pure**: it takes an array of candidate cards, the current
filters, and the recent-draw list, and returns the chosen cards. No database access, no
`fetch`, no imports from `server/`. It runs in the browser. This is the part that gets
unit-tested, and it is the part that matters.

Note the deliberate split: `draw.ts`, `cardId.ts`, `pool.ts` and `outbox.ts` are **not**
under `server/`, because they run on the client. Anything under `server/` never reaches
the browser bundle.

---

## 4. Data model

Epoch milliseconds for all timestamps. SQLite has no boolean — use `integer` with 0/1
and Drizzle's `mode: 'boolean'`.

```sql
CREATE TABLE cards (
  id          TEXT PRIMARY KEY,            -- see §5
  text        TEXT NOT NULL,
  favorite    INTEGER NOT NULL DEFAULT 0,
  status      TEXT NOT NULL DEFAULT 'active'
              CHECK (status IN ('active','archived')),
  author      TEXT NOT NULL
              CHECK (author IN ('david','claude')),
  draw_count    INTEGER NOT NULL DEFAULT 0,
  last_drawn_at INTEGER,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);

CREATE TABLE card_kinds (
  card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  kind    TEXT NOT NULL
          CHECK (kind IN ('idea','source','treatment','technique','constraint','arrangement')),
  PRIMARY KEY (card_id, kind)
);

CREATE TABLE card_gear (
  card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  gear_id TEXT NOT NULL REFERENCES gear(id) ON DELETE RESTRICT,
  PRIMARY KEY (card_id, gear_id)
);

CREATE TABLE card_tags (
  card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  tag     TEXT NOT NULL,
  PRIMARY KEY (card_id, tag)
);

CREATE TABLE gear (
  id       TEXT PRIMARY KEY,               -- slug
  name     TEXT NOT NULL,                  -- display name
  category TEXT NOT NULL
           CHECK (category IN ('strings','keys','amps','pedals','mics','small')),
  owned    INTEGER NOT NULL DEFAULT 1,
  in_rack  INTEGER NOT NULL DEFAULT 0,
  sort     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE draws (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  uuid     TEXT NOT NULL UNIQUE,           -- minted by the client, see §6
  drawn_at INTEGER NOT NULL,
  mode     TEXT NOT NULL CHECK (mode IN ('single','spread'))
);

CREATE TABLE draw_cards (
  draw_id   INTEGER NOT NULL REFERENCES draws(id) ON DELETE CASCADE,
  slot      INTEGER NOT NULL,              -- 0 for single; 0,1,2 for spread
  slot_kind TEXT,                          -- null for single
  card_id   TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  PRIMARY KEY (draw_id, slot)
);

CREATE TABLE settings (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL                      -- JSON
);

CREATE INDEX idx_card_kinds_kind    ON card_kinds(kind);
CREATE INDEX idx_card_gear_gear     ON card_gear(gear_id);
CREATE INDEX idx_cards_status       ON cards(status);
CREATE INDEX idx_draw_cards_card    ON draw_cards(card_id);
CREATE INDEX idx_draws_drawn_at     ON draws(drawn_at DESC);
```

### Notes on the model

- **Kinds are a join table, not a JSON column.** The spread queries filter by kind
  constantly; a join table keeps that a simple indexed lookup.
- **`card_gear` is `ON DELETE RESTRICT`.** You cannot delete a piece of gear that cards
  still reference. The gear screen must offer to reassign or show which cards block it.
- **`settings`** holds exactly three keys in phase 1: `theme` (`"dark" | "light" | "system"`),
  `drawFilters` (see §6), and `schemaVersion`.
- **No `weight` column yet.** Phase 2 adds `weight INTEGER NOT NULL DEFAULT 1` to `cards`
  and changes only the selection step in `draw.ts`. Leave a comment there saying so.

---

## 5. Card identity

Card IDs are content-derived so that re-importing the same file is a no-op.

```ts
function normalise(text: string): string {
  return text
    .normalize('NFC')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function cardId(text: string): string {
  return sha256(normalise(text)).slice(0, 12);  // hex
}
```

Consequences the importer depends on, and which you must not "improve":

- Editing a card's text in the UI **does not** change its id. The id is assigned once at
  creation. Store it; never recompute on update.
- The importer computes `cardId(text)` for each incoming card and looks it up. A hit means
  "this card already exists" — see §8.
- Two cards whose texts differ only in case or whitespace are the same card.

---

## 6. The draw

### Filters

Held in `settings.drawFilters`, edited from the draw screen, applied to every draw:

```ts
type DrawFilters = {
  inRackOnly: boolean;      // default true
  favouritesOnly: boolean;  // default false
  tags: string[];           // empty = any
};
```

### Eligibility

A card is eligible for a slot of kind `K` when **all** of:

1. `status = 'active'`
2. it has kind `K`
3. every gear it references has `owned = 1`
4. if `inRackOnly`, every gear it references also has `in_rack = 1`
5. if `favouritesOnly`, `favorite = 1`
6. if `filters.tags` is non-empty, the card has at least one of those tags
7. its id is not among the **last 30 card ids in `draw_cards`**, ordered by `draws.drawn_at DESC`
8. (spread only) it is not already filling another slot in the draw being built

A card with no gear passes 3 and 4 trivially. That is intended — "Mono. All of it." should
always be available.

### Selection

**Uniform random** over the eligible set. Nothing else. Not weighted, not
least-recently-drawn-first. The 30-card suppression is the only recency mechanism in
phase 1.

### When nothing is eligible

Relax in this exact order, and tell the user which step you had to take:

1. Shrink the suppression window from 30 to 10.
2. Drop the suppression window entirely.
3. Drop `inRackOnly`.
4. Show an empty state naming the filter that emptied the pool and linking to it.

Never silently return nothing, and never silently ignore a filter the user set.

### Modes

**Single** draws one card of kind `idea`. Slot kind is null.

**Spread** fills three slots in order — `source`, `treatment`, `constraint` — each from its
own kind pool, honouring rule 8 so the same card cannot appear twice in one spread.

**Locking** a slot means the next "Draw" leaves that slot alone and redraws the others.
Lock state is client-side only; it is not persisted.

**Reroll** redraws one slot. It updates the existing `draw_cards` row for that slot rather
than creating a new draw.

### Recording a draw

Every draw — including single-slot draws and rerolls — is recorded: it writes to `draws` /
`draw_cards`, and increments `draw_count` and sets `last_drawn_at` on each card involved.
This feeds the suppression window and the manager's "drawn N×" column.

The **client** performs the draw and then posts the record. Because the post may be
queued offline and replayed later, the client generates the draw's identity, and the
endpoint must be idempotent:

- The client mints a `uuid` and a `drawn_at` at draw time and includes both.
- `draws` therefore carries a `uuid TEXT NOT NULL UNIQUE` column alongside its integer
  primary key. Add it to the schema in §4.
- `POST /api/draws` upserts on `uuid`. Replaying the same record twice must not
  double-count `draw_count`.
- The suppression window is computed client-side from the cached recent-draw list, which
  includes queued-but-unsent draws. Drawing offline still avoids repeats.

### History

The draw screen has back/forward through tonight's draws. "Tonight" is the draws from the
last 8 hours. Purely a read of `draws` ordered by `drawn_at`; stepping back does not
re-record anything.

---

## 7. Gear inventory

Seed the `gear` table with exactly this. Slugs are the vocabulary for card imports and the
card-generation skill, so they must match character-for-character.

**strings**
`fender-strat` Fender Stratocaster (MX) ·
`aria-strat` Aria strat ·
`epiphone-sg` Epiphone SG ·
`gretsch-12` Gretsch 12-string ·
`nylon-classical` Nylon classical ·
`bandolim` Bandolim ·
`guitarra-portuguesa` Guitarra portuguesa ·
`aria-bass` Aria bass ·
`flamenca` Flamenca *(owned: false)* ·
`steel-string` Steel-string acoustic *(owned: false)*

**keys**
`rd-08` Roland RD-08 ·
`jv-880` Roland JV-880 ·
`mpc-one-plus` MPC One+ ·
`circuit-tracks` Circuit Tracks ·
`circuit-rhythm` Circuit Rhythm ·
`volca-keys` Volca Keys ·
`sk-1` Casio SK-1

**amps**
`ac30` Vox AC30 ·
`pathfinder-15r` Pathfinder 15R ·
`pathfinder-bass-10` Pathfinder Bass 10

**pedals**
`fuzz-factory` Fuzz Factory (custom) ·
`boss-fz5` Boss FZ-5 ·
`french-toast` Danelectro French Toast ·
`hardwire-od` DigiTech Hardwire OD ·
`boss-phaser` Boss phaser ·
`eleclady` Mooer ElecLady ·
`pitch-fork-plus` EHX Pitch Fork+ ·
`boss-volume` Boss volume pedal ·
`flashback-2` TC Flashback 2 ·
`hall-of-fame-2` TC Hall of Fame 2 ·
`synth-wah` DigiTech Synth Wah ·
`mooer-octaver` Mooer octaver ·
`artec-eq` Artec parametric EQ ·
`rp90` DigiTech RP90

**mics**
`xm8500` Behringer XM8500 ·
`cm25` Focusrite CM25 MkIII ·
`h4essential` Zoom H4essential ·
`sm57` Shure SM57 *(owned: false)* ·
`ga-r1` Golden Age R1 MkIII *(owned: false)* ·
`c2-pair` Behringer C2 pair *(owned: false)*

**small**
`harmonicas` Harmonicas ·
`melodica` Melodica ·
`djembes` Djembés ·
`kalimba` Kalimba ·
`flutes` Flutes ·
`ocarina` Ocarina ·
`the-tube` The tube

That is **42 owned** and **5 on the wishlist**. (A header in the mockups reads "16 of 41" —
the mockup is wrong, this list is right.)

### Rules

- **`owned: false` is the wishlist.** Cards may reference wishlist gear so that ideas can
  be written ahead of a purchase, but those cards never appear in a draw. The manager greys
  them. When the owner buys the thing, he flips `owned` and the cards come alive.
- **`in_rack`** is "patched and reachable tonight", toggled on the gear screen. It is the
  one piece of state the owner changes most often. It is global, not per-session.
- **Always-on gear is absent from this table.** The AR16c, the ADAM T5V monitors,
  headphones and the laptop are always in the chain and choosing them means nothing. Cards
  may name them in their *text* — "Nothing added after the AR16c" is a real card — but
  there is no gear row and no gear reference. If the owner later asks to add a monitor,
  push back before doing it.
- The owner can add gear from the UI. New slugs must match `^[a-z0-9][a-z0-9-]*$`.

---

## 8. Import and export

### Format

One YAML document. `.yaml`, `.yml` and `.json` all accepted (JSON is a subset of YAML and
needs no separate code path).

```yaml
# sótão cards — import v1
version: 1
defaults:
  author: claude
cards:
  - text: >-
      Play the stage piano through the pitch shifter at plus one octave,
      reamp it into the valve combo, and mic the amp from the staircase
      opening.
    kinds: [idea]
    gear: [stage-piano, pitch-shifter, valve-combo, condenser]
    tags: [reamping, room]

  - text: toy sampler, sampled from something already in the room.
    kinds: [idea, source]
    gear: [toy-sampler]
    tags: [sampling, lo-fi]
    favorite: true

  - text: Mono. All of it.
    kinds: [constraint]
```

### Schema

| Field | Type | Required | Default |
|---|---|---|---|
| `version` | `1` | yes | — |
| `defaults` | object | no | `{}` |
| `defaults.author` | `david` \| `claude` | no | `claude` |
| `defaults.tags` | string[] | no | `[]` — merged with each card's own tags |
| `cards` | array, min 1 | yes | — |
| `cards[].text` | string, 1–500 chars | yes | — |
| `cards[].kinds` | array of kind, min 1 | yes | — |
| `cards[].gear` | array of gear slug | no | `[]` |
| `cards[].tags` | array of string | no | `[]` |
| `cards[].favorite` | boolean | no | `false` |
| `cards[].author` | `david` \| `claude` | no | from `defaults` |

Reject unknown keys at every level rather than ignoring them — a typo like `kind:` for
`kinds:` must be an error, not a silently dropped field.

### Validation

Validation is **whole-file, all-or-nothing at the file level, per-card at the row level**:
parse the entire document, collect every error, and show them all at once. Never import
half a file and stop at the first problem.

Each error carries a **line number** (from `parseDocument`'s node ranges), the card index,
and a message written for a human:

> `Line 32 · card 5` — Gear `instrument-dynamics` is not in your inventory. Did you mean `instrument-dynamic`? Fix the
> file, or add the gear first — this card will be skipped.

Suggest a correction using Levenshtein distance ≤ 2 against known gear slugs.

Cards with errors are **skipped**; valid cards in the same file still import. The preview
must say exactly how many of each.

### Diffing

For each valid card, compute `cardId(text)` and classify:

| Class | Condition | Action |
|---|---|---|
| **New** | id not in `cards` | insert |
| **Updated** | id exists, and kinds/gear/tags/favorite differ | update those fields, bump `updated_at` |
| **Unchanged** | id exists, everything matches | no write |
| **Error** | failed validation | skip |

An import **never** changes `status`, `author`, `draw_count`, `last_drawn_at` or
`created_at` on an existing card. Re-importing a file that has already been imported must
produce zero writes and report everything as Unchanged. Test this.

### Flow

Paste into a textarea or choose a file → preview showing the four counts, a per-card list
with its class, and the error detail → **Import N cards** commits in a single transaction.
Nothing is written before that button.

### Export

`GET /api/export.yaml` returns every card — including archived ones, with
`status: archived` on those — in exactly the import format, so a round-trip is lossless.
This is the backup story. Offer it from the manager toolbar.

---

## 9. Screens

Build from the mockups. This section covers behaviour the pictures cannot show.

### Draw (`/draw`)

The signature screen. It is read from across a dim room while holding an instrument, so:
card text is large, the ground is dark by default, and the primary action is a single
oversized button.

- **Keyboard**: `Space` draws. `1` `2` `3` toggle the lock on each spread slot. `←` `→` step
  through tonight's history. `F` toggles favourite on the focused card. Bind these on the
  page, not on `window`, and never when focus is in a text input.
- Mode is a URL param (`?mode=single` / `?mode=spread`) so the phone can be installed
  pointing straight at one.
- The header chip shows `In the rack · N of 42` and links to `/gear`.
- Drawing is instant because it happens locally — no spinner, no round-trip. The record
  posts in the background.
- When the pool cache is stale or the last sync failed, show a small unobtrusive marker in
  the filter strip (the mockup has "Offline · cached" in that spot). Never block a draw on
  a sync.

### Manager (`/cards`)

Dense table. Columns: card text, kinds, gear, tags, favourite, drawn count, author.

- Search is substring, case-insensitive, over card text — debounce 150 ms.
- Filter chips: kind (multi-select), author, status, gear, favourites.
- Default sort: least recently drawn first, `NULL` (never drawn) at the top.
- Clicking a row opens the editor.
- Cards referencing unowned gear render in a dimmed text colour.

### Editor (`/cards/[id]`, and `/cards/new`)

- Kinds are **checkboxes**, not radios. Below them, a live sentence explains what the
  current combination means: "drawn on its own, and into the technique slot" / "drawn into
  spreads only" / "not drawable yet — pick at least one". Saving with zero kinds is
  blocked.
- Gear is a chip list plus a typeahead limited to inventory slugs. Free text is rejected
  with a link to add the gear.
- Favourite is a single checkbox. Its label must **not** promise any effect on how often
  the card appears — it is a marker. Label it "Favourite" and leave it at that.
- **Archive** is the soft delete and the default action. **Delete** is separate, permanent,
  and asks for confirmation; it cascades through `card_kinds`, `card_gear`, `card_tags` and
  `draw_cards`.

### Gear (`/gear`)

- Grouped by category, two columns. Each row is a checkbox for `in_rack`.
- "Everything" and "Clear" bulk actions.
- Wishlist items are disabled for `in_rack` and badged.
- The always-on rule is stated in the page copy — it is the explanation for why the
  monitors and the desk are not listed, and the owner will forget otherwise.

### Import (`/import`)

Source pane with line numbers on the left, preview on the right. See §8.

---

## 10. Auth

```
APP_PASSWORD   the single password
APP_SECRET     32+ random bytes, for signing the cookie
```

- `/login` posts the password. Compare in constant time.
- On success set `sotao_session`, value = HMAC-SHA256 signed payload `{iat}`, `httpOnly`,
  `secure`, `sameSite=lax`, `maxAge` one year.
- `hooks.server.ts` guards everything except `/login` and static assets.
- Rate-limit failed attempts: 10 per 15 minutes per IP, in memory. Good enough for one
  user.
- No password reset, no "remember me" (it always remembers), no logout button in the
  header — put logout in a settings menu where it will not be hit by accident.

---

## 11. PWA and offline

Offline is not a later layer bolted onto a server-rendered app. The draw is a client
concern from the start, and the server is a store. That is one code path rather than two,
which is the whole reason for the arrangement.

### Manifest and shell

- `manifest.webmanifest`: name "Sótão Cards", `display: standalone`, dark theme colour,
  icons at 192/512, `start_url: /draw?mode=single`.
- A service worker precaches the app shell, JS, CSS and fonts. Launching from the home
  screen must work with the network off.
- Installable on iOS and Android.

### The pool

`GET /api/pool` returns everything the client needs to draw:

```ts
type Pool = {
  syncedAt: number;
  cards: {
    id: string; text: string; kinds: Kind[]; gear: string[];
    tags: string[]; favorite: boolean;
  }[];                        // active cards only
  gear: { id: string; owned: boolean; inRack: boolean }[];
  recent: string[];           // last 30 drawn card ids, newest first
  filters: DrawFilters;
};
```

A few hundred cards is a few tens of kilobytes — do not paginate it, do not try to send
deltas. Fetch it on load, store it in IndexedDB under one key, and render from the stored
copy immediately while the fetch is in flight so the first paint never waits on the
network.

Refresh the pool on: app load, regaining focus after more than five minutes, any card or
gear mutation, and a successful outbox flush.

### The outbox

Queued draw records live in IndexedDB. On each draw, append the record and try to flush.
Flush on reconnect (`online` event) and on app load.

- Records carry the client-minted `uuid`, so replays are idempotent (§6).
- A flush failure is not an error the user sees. It retries.
- Locally, append the drawn ids to the cached `recent` list immediately, so suppression
  works across offline draws.
- If a record is rejected with a 4xx (a card was deleted on another device, say), drop it
  from the queue and move on. Do not retry forever, and do not surface it.

### What must actually work with the network off

Test these by hand before calling M5 done:

1. Launch from the home screen → the draw screen renders.
2. Draw twenty times in both modes → no repeats within 30, spreads never duplicate a card.
3. Reconnect → every queued draw lands, `draw_count` is correct, nothing double-counted.

Editing cards, gear and import are **online-only** and may show a plain "needs a
connection" state. Only drawing has to work offline.

---

## 12. Theming

Both themes are designed; neither is an afterthought. Define every colour as a CSS custom
property on `:root`, redefine the tokens (never the components) for the other theme.

Support three states: explicit dark, explicit light, and follow-the-system. Persist the
choice in `settings.theme`, and apply it server-side on first render so the app does not
flash the wrong theme.

**Dark (default)**

| Token | Value |
|---|---|
| `--ground` | `#101722` |
| `--panel` | `#18212F` |
| `--rail` | `#131C28` |
| `--line` | `#2C3A4E` |
| `--text` | `#ECE4D6` |
| `--text-dim` | `#98A4B4` |
| `--text-faint` | `#6C7A8C` |

**Light**

| Token | Value |
|---|---|
| `--ground` | `#EEE8DA` |
| `--panel` | `#FAF7EF` |
| `--rail` | `#E6DFCD` |
| `--line` | `#D2C8B2` |
| `--text` | `#1B2430` |
| `--text-dim` | `#56606E` |
| `--text-faint` | `#6E7788` |

**Kind colours** — semantic, and they carry meaning across every screen, so they are
tokens too. Dark / light:

| Kind | Dark | Light |
|---|---|---|
| idea | `#ECE4D6` | `#1B2430` |
| source | `#74A79A` | `#3E6F62` |
| treatment | `#D9A44A` | `#8A6210` |
| technique | `#8E9BD4` | `#43508C` |
| constraint | `#E0685B` | `#A83527` |
| arrangement | `#B99BC9` | `#7A4E86` |

**Type**

- Display / card text: **Familjen Grotesk** 600 — the large card prompt, headings, the
  Draw button.
- UI / body: **IBM Plex Sans** 400/500.
- Labels, gear chips, counts, code: **IBM Plex Mono** 400/600, uppercase with
  `letter-spacing: 0.1em–0.22em` for labels. The monospace labelling is what gives the app
  its equipment-panel character — do not substitute the body face to "simplify".

Load from Google Fonts with `display=swap` and real fallback stacks. Numbers that line up
in columns get `font-variant-numeric: tabular-nums`.

---

## 13. Routes

| Route | Method | Purpose |
|---|---|---|
| `/` | GET | redirect → `/draw?mode=single` |
| `/draw` | GET | draw screen |
| `/api/pool` | GET | everything the client needs to draw (§11) |
| `/api/draws` | POST | record a completed draw; upserts on `uuid`, idempotent |
| `/cards` | GET | manager |
| `/cards/new`, `/cards/[id]` | GET | editor |
| — | POST | form actions: create, update, archive, delete |
| `/gear` | GET | inventory |
| — | POST | form actions: toggle in_rack, bulk set, add gear, set owned |
| `/import` | GET | importer |
| `/api/import/preview` | POST | YAML text → the diff report, writes nothing |
| `/api/import/commit` | POST | YAML text → transactional insert/update |
| `/api/export.yaml` | GET | full export |
| `/login` | GET, POST | |

Prefer SvelteKit form actions for mutations driven by forms; use the JSON endpoints only
where the client genuinely needs a response object (pool, draw records, import preview).

There is no endpoint that *performs* a draw. If you find yourself writing one, the
architecture has drifted — re-read §11.

---

## 14. Testing

Vitest. Cover the logic, not the markup. These are the tests that must exist:

**`cardId.ts`**
- Case, whitespace and Unicode-normalisation differences produce the same id.
- Different text produces different ids.

**`draw.ts`** — pure functions, so test them directly with fixture arrays
- A card whose gear is out of the rack is excluded when `inRackOnly`, included when not.
- A card referencing unowned gear is excluded in **both** cases.
- A card with no gear is always eligible.
- The last 30 drawn ids are excluded; the 31st is eligible again.
- In a spread, a card holding two of the three slot kinds cannot fill two slots.
- The relaxation ladder fires in the right order when the pool is empty.
- Selection is uniform: over 10 000 draws from a pool of 5, each appears 2 000 ± 200 times.

**`import.ts`**
- A valid file imports with the right counts.
- An unknown gear slug produces an error with the right line number and suggests the
  nearest slug.
- An unknown key (`kind:` instead of `kinds:`) is an error, not a silent drop.
- Importing the same file twice: the second run reports all Unchanged and performs **zero**
  writes.
- Changing one card's tags and re-importing reports exactly one Updated.
- A file with one bad card and four good ones imports four and reports one error.

**`outbox.ts`**
- A queued record survives a reload.
- Posting the same `uuid` twice leaves `draw_count` unchanged the second time.
- A 4xx response drops the record from the queue rather than retrying.

**Round-trip**
- Export → import produces zero changes.

---

## 15. Build order

Ship each milestone working before starting the next. Do not build all the screens against
a stub database.

**M0 — Foundations.** Scaffold, Tailwind, Drizzle, schema, migrations, login, auth guard,
theme tokens and the switcher. Acceptance: you can log in, the app renders in both themes,
migrations run clean on an empty database.

**M1 — Gear.** Inventory screen, seed data from §7, in-rack toggles, add gear.
Acceptance: all 47 rows seeded, toggles persist, the count in the header is right.

**M2 — Cards.** Manager table, filters, search, editor, archive, delete. Acceptance: a card
can be written, edited, archived and deleted; multi-kind selection works; gear typeahead
rejects unknown slugs.

**M3 — Draw.** `draw.ts` with its tests first, then the pool cache, both modes, locking,
reroll, history, and the outbox. Acceptance: the draw tests in §14 pass; drawing 40 times
never repeats a card within 30; the spread never shows the same card twice; posting the
same draw record twice does not double-count.

**M4 — Import / export.** Parser, validator, diff, preview, commit, export. Acceptance: the
import tests in §14 pass, including the zero-writes-on-reimport case.

**M5 — Finish.** PWA manifest, service worker, keyboard shortcuts, empty states, phone
layout pass, seed the starter cards. Acceptance: the three offline scenarios in §11 pass
by hand with the network disabled, and every screen works at 390 px wide.

### Working notes for the agent

- Commit per milestone with a real message. Do not force-push.
- If something in this document turns out to be wrong or impossible, stop and say so in
  your reply rather than silently substituting a different design.
- The owner is a .NET developer who knows TypeScript well. Write code he can maintain:
  boring, explicit, no clever abstractions, no premature generalisation for the phase-2
  features listed in §2.
- Where this document and the mockups disagree about *behaviour*, this document wins. Where
  they disagree about *layout*, the mockups win.

---

## Appendix A — the card-generation skill

The owner wants to talk to Claude about ideas and get back a file he can import. That is a
skill, created separately from this build, but it depends on the format this app defines.
Build the app first; the skill's contract is below so the two stay in step.

**Name:** `sotao-cards`

**Description:** Generate inspiration cards for the Sótão Cards app as a YAML file ready
for bulk import. Use when David asks for cards, prompts or ideas for studio experiments.

**What it must contain:**

1. The full gear slug vocabulary from §7, with the note that wishlist items are allowed but
   will not be drawn until owned.
2. The six kinds, what each means, and the rule that `idea` makes a card standalone while
   the other five make it combinable — and that both together is fine and often best.
3. The exact YAML schema from §8, with `version: 1` and `defaults: {author: claude}`.
4. Writing guidance, which is the part that decides whether the cards are any good:
   - Second person, imperative, present tense. "Sample yourself whistling into the toy sampler",
     not "One could try sampling".
   - One action per card. If it needs an "and then", it is probably two cards.
   - Name specific gear by its real name. Vague cards are useless cards.
   - A `source` card names a sound. A `treatment` names something done to a sound. A
     `constraint` is a rule that removes an option. Do not write a `constraint` that is
     really an instruction.
   - Under 160 characters for combinable kinds; up to 300 for a standalone `idea`.
   - No card that requires gear he does not own unless he asked for wishlist cards.
   - Avoid genre prescriptions. The cards suggest a move, not a style.
5. An instruction to output the YAML in a fenced block, or to write it to a file when the
   session can, and to say how many cards of each kind it produced.

---

## Appendix B — starter cards

Seed `seed/cards.yaml` with these so the app is usable the moment it is built. All
`author: claude`. They are also the fixtures the owner will judge the draw screen by, so
keep the wording.

```yaml
version: 1
defaults:
  author: claude
cards:
  - text: >-
      Play the stage piano through the pitch shifter at plus one octave, reamp it into
      the valve combo, and mic the amp from the staircase opening.
    kinds: [idea]
    gear: [stage-piano, pitch-shifter, valve-combo, condenser]
    tags: [reamping, room]

  - text: >-
      Sample yourself whistling into the toy sampler, pitch it down two octaves, and
      build the bass line out of that.
    kinds: [idea, source]
    gear: [toy-sampler]
    tags: [sampling, lo-fi]
    favorite: true

  - text: >-
      Record the bandolim with the condenser up close and the handheld recorder at the
      bottom of the stairs. Blend until the room is half the sound.
    kinds: [idea, technique]
    gear: [bandolim, condenser, handheld-recorder]
    tags: [room, stereo]

  - text: >-
      Loop a djembé pattern on the drum groovebox, then play guitarra
      portuguesa over it in one take. Keep the take where you lost the thread.
    kinds: [idea]
    gear: [drum-groovebox, guitarra-portuguesa, djembes]
    tags: [one-take]

  - text: >-
      Run the mini synth into the octave fuzz, then into the practice combo
      turned up past where it stays clean. One microphone, three metres back.
    kinds: [idea, treatment]
    gear: [mini-synth, octave-fuzz, practice-amp, dynamic-mic]
    tags: [saturation, room]

  - text: >-
      Play the ocarina and the melodica at the same time, one in each hand,
      into the condenser. Do not fix the timing afterwards.
    kinds: [idea, technique]
    gear: [ocarina, melodica, condenser]
    tags: [one-take, winds]

  - text: toy sampler, sampled from something already in the room.
    kinds: [source]
    gear: [toy-sampler]
    tags: [sampling, lo-fi]
    favorite: true

  - text: 12-string, tuned down until it rattles.
    kinds: [source]
    gear: [twelve-string]
    tags: [strings]

  - text: Melodica, into a microphone — never a DI.
    kinds: [source, technique]
    gear: [melodica, dynamic-mic]
    tags: [winds]

  - text: A rack synth patch you would never choose on purpose.
    kinds: [source]
    gear: [rack-synth]
    tags: [presets]

  - text: Kalimba, close-miked, one metre off the slab.
    kinds: [source]
    gear: [kalimba, condenser]
    tags: [small]

  - text: The tube. Whatever it is doing today.
    kinds: [source]
    gear: [the-tube]
    tags: [found]

  - text: Guitarra portuguesa, played like a percussion instrument.
    kinds: [source, technique]
    gear: [guitarra-portuguesa]
    tags: [strings]

  - text: pitch shifter at minus twelve, wet only.
    kinds: [treatment]
    gear: [pitch-shifter]
    tags: [octave]
    favorite: true

  - text: octave fuzz into the overdrive, gain staged wrong on purpose.
    kinds: [treatment]
    gear: [octave-fuzz, overdrive]
    tags: [fuzz]

  - text: reverb before the amp, not after.
    kinds: [treatment]
    gear: [reverb, valve-combo]
    tags: [reverb]

  - text: delay with the mix pinned at 100%.
    kinds: [treatment]
    gear: [delay]
    tags: [delay]

  - text: envelope filter on a source that is not a guitar.
    kinds: [treatment, constraint]
    gear: [envelope-filter]
    tags: [filter]

  - text: multi-effect, first preset, no editing allowed.
    kinds: [treatment, constraint]
    gear: [multi-effect]
    tags: [lo-fi]

  - text: flanger on the way in, bass combo on the way out.
    kinds: [treatment]
    gear: [flanger, bass-practice-amp]
    tags: [phaser]

  - text: Mono. All of it.
    kinds: [constraint]
    tags: [mono]
    favorite: true

  - text: Nothing quantised.
    kinds: [constraint]
    tags: [feel]

  - text: Four tracks maximum.
    kinds: [constraint]
    tags: [limits]

  - text: Nothing added after the desk.
    kinds: [constraint]
    tags: [limits]

  - text: Three minutes, then you stop and listen back once.
    kinds: [constraint]
    tags: [limits]

  - text: One microphone. Move it instead of reaching for another.
    kinds: [constraint, technique]
    tags: [mics]

  - text: Play it at half speed, then double the session tempo afterwards.
    kinds: [technique]
    tags: [lo-fi]

  - text: Mic it from three metres away and commit to that take.
    kinds: [technique]
    tags: [room]

  - text: Two instruments at once, one hand each.
    kinds: [technique]
    tags: [one-take]

  - text: Use it as the intro only. Everything else gets built after.
    kinds: [arrangement]
    tags: [structure]

  - text: The loudest thing in the mix is the quietest instrument you own.
    kinds: [arrangement]
    tags: [structure]

  - text: Build the whole thing from one bar.
    kinds: [arrangement]
    tags: [structure]
```
