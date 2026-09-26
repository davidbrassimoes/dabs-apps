# Sótão — studio cards

Draws a card when DABS sits down in the studio and does not know where to start: one idea,
or three partial cards (a source, a treatment, a constraint) that combine. Plus a card
manager, a gear inventory and a YAML import. SvelteKit on Cloudflare Workers, libSQL on
Turso, installable on a phone and usable offline.

An **unpublished Escarpa Audio tool** (`handbook/decisions/ADR-017-sotao.md`). It is his own
tool, built for one attic and one rig. The rules it obeys as a product are
`handbook/02-product-rules.md`, section "Tools". The ones that wait for publication are
listed in ADR-017. This file covers the rules it obeys as a codebase. It wins here, and the
handbook's engineering page is about plugins.

**`docs/IMPLEMENTATION.md` is the build contract.** Its behaviour, formats and numbers are
binding. ADR-017 overrides it in three places, listed at its top: hosting (§3), theming
(§12) and the inventory (§7). `docs/mockups/` is the layout reference and predates sea
fog, so take layout from it, never colour or type.

## Before you change anything

- **`src/lib/draw.ts` is pure.** It takes candidate cards, filters and the recent-draw list
  and returns cards. No database, no `fetch`, nothing from `server/`. It runs in the
  browser, and it is the part that matters (§3, §6).
- **`draw.ts`, `cardId.ts`, `pool.ts` and `outbox.ts` are not under `server/`** because they
  run on the client. Anything under `server/` never reaches the browser bundle.
- **Out of scope for phase 1, so do not build it** (§2): draw-odds weighting, configurable
  spreads, per-card notes or logs, attachments, a second user.
- **One user, one password, no users table** (§10).
- **Always-on gear has no row**: the desk, the monitors, headphones, the laptop. Push back
  before adding one (§7).

## Real names never go in git

The app's whole vocabulary is real gear, and rule H1 keeps real product and manufacturer
names out of the repository. `check-names.sh` greps it on every push. So:

| File                                              | What                                                       | In git             |
| ------------------------------------------------- | ---------------------------------------------------------- | ------------------ |
| `seed/gear.local.ts`, `seed/cards.local.yaml`     | The real rig and starter cards                             | **No**, gitignored |
| `seed/gear.example.ts`, `seed/cards.example.yaml` | The same rig described generically, row for row            | Yes                |
| `seed/index.ts`                                   | Uses the local files if they exist, otherwise the examples | Yes                |

Tests, fixtures, mockups, comments, placeholders and docs use the **example's** slugs
(`toy-sampler`, `valve-combo`), never a real model name. If a test needs a gear name,
invent one. The real names belong in the database and the local files only. Card imports
live in `imports/`, which is gitignored.

## Look

Sea fog, Morning and Night (ADR-012). A tool takes the marks, the three faces, one accent,
and both themes' ground and ink (ADR-004). `src/routes/layout.css` holds every colour as a
token, and its header says where each one comes from.

- **Never write a literal colour in a component.** Add a token to both themes, and measure
  it: 4.5:1 for text on ground, panel, rail and chip, and 3:1 for graphics. The measured
  values are in `NOTES.md`, "Palette".
- **Faces:** Cormorant Garamond for the name and card text (always `lining-nums`), Jost for
  reading, DM Mono for labels and values. They are bundled from `@fontsource`, with no
  third-party request (ADR-008). DM Mono has no 600, so ask for 500.
- **`--accent`** is the brand's pointer ink, for focus, selection and the active tab.
  **`--lamp`** is Ochre, the product's accent, and appears only on the About block.
- **The theme switch** is Auto, Morning and Night, with Auto as the default, falling back to
  Morning (`src/lib/theme.ts`).
- **UI text follows the voice** in `handbook/01-brand.md`: plain, short, second person on
  the cards, no hype.

## Commands

All from `products/sotao/`.

|                                  |                                                                                     |
| -------------------------------- | ----------------------------------------------------------------------------------- |
| `npm run dev`                    | Dev server. Needs `.env` (see `.env.example`): `DATABASE_URL=file:local.db` locally |
| `npm test`                       | Vitest. Sets its own in-memory `DATABASE_URL`, so it needs no `.env`                |
| `npm run check` / `npm run lint` | svelte-check and tsc, then prettier and eslint                                      |
| `npm run build`                  | The Workers build                                                                   |
| `npm run deploy`                 | Build and `wrangler deploy`. From DABS's machine only, so the real seed ships       |
| `npm run db:generate`            | A new migration from `schema.ts`. It is committed and bundled at build time         |
| `npm run icons`                  | Redraws `static/` icons from the palette                                            |

**Migrations are bundled, not read from disk** (`src/lib/server/db/migrations.ts`), because
a Worker has no filesystem. They are applied in drizzle's format, and a test holds the
bundle to drizzle's own reader. Generate with drizzle-kit as usual.

The card-generation skill lives at the repository root, `.claude/skills/sotao-cards/`. Its
script reads this folder.

## Session discipline

End each session by writing what changed to `NOTES.md`. A decision that changes what gets
built goes in `docs/IMPLEMENTATION.md`, or in an ADR if it touches the company's rules.
