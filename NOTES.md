# Session notes

The state of Sótão on one page. **Write to it at the end of every session**: what
changed, what was decided, what is next. It is not a changelog and not a decision record.
Decisions go in `docs/IMPLEMENTATION.md`, or in an ADR when they touch the company's rules.

## Where it stands

|        |                                                          |
| ------ | -------------------------------------------------------- |
| Built  | M0–M5 of `docs/IMPLEMENTATION.md` §15, all of phase 1    |
| Status | Unpublished Escarpa Audio tool (ADR-017). Never deployed |
| Next   | The first deploy, then the two checks it opens (below)   |

## 2026-09-26 — brought into the company repository (ADR-017)

- **Moved** from `davidbrassimoes/sotao-cards` by `git subtree add`, history intact. The old
  repository is archived.
- **Renamed** to _Sótão — studio cards_. Titles, the manifest, the package, the export
  filename (`sotao-<date>.yaml`; the skill also reads the old `sotao-cards-` name), the
  service-worker cache and the IndexedDB outbox (`sotao`) all changed. Nothing was
  deployed, so nothing was stranded.
- **Real names out of git**: the local and example seed split described in `CLAUDE.md`.
  The real files were moved as they were, and `gear.local.ts` only changed its type
  import.
- **Sea fog**, both themes, the three faces, the Ochre lamp and the About block on
  Settings. Themes stored under the old names are mapped (`dark` → `night`,
  `light` → `morning`, `system` → `auto`). The icons were redrawn by `scripts/icons.mjs`.
- **Cloudflare Workers + Turso** replace Vercel. `adapter-cloudflare`, `wrangler.jsonc`,
  and migrations bundled instead of read from disk.
- **CI**: the `sotao` job in `.github/workflows/build.yml` runs check, lint, the tests
  (with a count floor) and the Workers build when this folder changes.
- The card skill moved to the repository root.

## Before the first deploy — what DABS does

Each step involves a secret or an outward action, so none of it is scripted.

1. **Copy `.env`** from the old checkout (`../sotao-cards/.env`) into this folder for local
   dev. It was not copied in the move.
2. **Create the Turso database** and a token for it.
3. **Set the four secrets**, from this folder: `npx wrangler secret put DATABASE_URL`, and
   the same for `DATABASE_AUTH_TOKEN`, `APP_PASSWORD` and `APP_SECRET`.
4. **`npm run deploy`** from this machine, so the bundle carries `seed/*.local.*`. The first
   request migrates and seeds the empty database.
5. **Back up `seed/*.local.*`** somewhere other than this machine. They are the only copy of
   the rig list outside the database.

## Open

- **Unverified against real Turso.** `wrangler deploy --dry-run` bundles libSQL's web client
  and no native binding (371 KiB gzipped). `wrangler dev` against an unreachable database
  boots, serves assets and fails only on the connection. Nothing has talked to a real
  database from a Worker yet.
- **`PRAGMA foreign_keys = ON` over HTTP.** `migrateDb` sets it once per isolate. Over
  Turso's HTTP protocol each request may be a new connection, and the pragma is
  per-connection. If it doesn't hold, the cascades on `card_kinds`/`card_gear`/`card_tags`
  and the RESTRICT that protects referenced gear stop working, with no error. Test it on the
  first deploy by deleting gear a card uses, which must fail. If it passes, turn it into a
  test.
- **The accent is proposed, not assigned.** Ochre (`brand/tokens.md`, "Accents"). DABS
  assigns it.
- **Keyboard access (T4) is not audited** against the new styles. The focus ring is now
  `--accent`, the ink. Check that it is visible on every control in both themes.
- **A custom domain** is a separate decision. For now it is reached at `workers.dev`.
- **Gear has no export.** Cards round-trip through `/api/export.yaml`, and gear exists only
  in the database and the local seed file.

## Palette

Measured with WCAG 2.2 relative luminance. The worst case is over ground, panel, rail and
chip-bg, the surfaces text sits on. The floor for text is 4.5:1.

| Token                                               | Morning   | Night     |
| --------------------------------------------------- | --------- | --------- |
| `text`                                              | 10.8      | 10.0      |
| `text-dim`                                          | 7.4       | 6.4       |
| `text-faint`                                        | 5.2       | 5.1       |
| `text-muted`                                        | 4.6       | 4.7       |
| `accent`                                            | 10.8      | 10.9      |
| `kind-source`                                       | 4.5       | 4.7       |
| `kind-treatment`                                    | 4.5       | 5.7       |
| `kind-technique`                                    | 4.5       | 4.7       |
| `kind-constraint`                                   | 4.5       | 4.5       |
| `kind-arrangement`                                  | 4.5       | 5.2       |
| `lamp` (graphic) on panel                           | 5.4       | 6.0       |
| `fav-star` (graphic) on panel / `fav-bg`, floor 3:1 | 3.6 / 3.1 | 4.2 / 3.5 |

Dividers (`line` on panel, about 1.8:1) are decorative, and nothing is identified by a
divider alone. The kind colours were moved from §12's hues by lightness only, as
`brand/tokens.md` does for accents. Night's `text-faint` and `text-muted` were raised
from the first derivation, where `text-muted` measured 4.1, and Morning's `fav-star` was
darkened from 2.9:1.
