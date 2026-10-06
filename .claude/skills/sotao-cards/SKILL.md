---
name: sotao-cards
description: Write inspiration cards for the Sótão Cards app as an import-ready YAML file (import v1, author claude), from a card count plus optional ideas or themes from David. Use this whenever David asks for cards, prompts, studio ideas, "twenty more sources", constraints, treatments, or anything to feed the app's /import screen — even when he does not say "skill", "YAML" or "import". Also use it when he is only kicking ideas around and wants them turned into cards.
---

# Sótão Cards — card generation

Sótão Cards deals David a card when he sits down in the studio and does not know where to
start. The cards live or die on whether they name a real move he can make tonight with gear
he actually owns. A vague card is worse than no card — it gets skipped, and a pool full of
skipped cards stops being worth drawing from.

Your job: turn a count, and optionally a few of his own ideas, into a YAML file the app
imports without a single error.

## The two inputs

`/sotao-cards <count> [ideas]`

- **count** — how many cards. Required. If he did not give a number, ask for one rather than
  guessing; "some cards" could mean five or fifty and the mix depends on it.
- **ideas** — free text. Optional, and often long: several ideas at once, some concrete and
  some abstract, mixed in with gear names, moods, asides and instructions about the batch
  itself. When present it outranks every default in this skill except the schema. See
  *Reading the input* below — this is the part that decides whether the batch feels like his
  ideas or like generic cards wearing them.

Examples of what arrives and what it means:

| He types | Read it as |
|---|---|
| `12` | 12 cards, your choice of mix, spread across the kinds a draw needs |
| `20 melodica and kalimba, wintery, nothing quantised` | 20 cards built around those two instruments and that feel; turn "nothing quantised" into a constraint if one does not already exist |
| `6 more constraints, harsher than the ones I have` | 6 cards, all `constraint`, deliberately more restrictive than the library |
| `15 ideas for when the valve combo is already patched` | 15 cards that assume the valve combo is in the rack |

## Reading the input

A long input is not one brief, it is a pile of seeds at different stages of cooking. Read the
whole thing before writing anything, then break it into the distinct seeds it actually
contains and decide what each one is. Fragments come in four flavours, and a single paragraph
often holds all four:

**Already a card.** A concrete move in his own words — "run the harmonica into the French
Toast", "the tube, hit with a mallet". Lift it. Keep his wording and his rhythm; tighten
only for length, fix only what the schema demands (a gear slug, a kind). Do not rewrite it
into your own register — he will recognise his line and know whether you listened. Set
`author: david` on that card, since the manager filters and labels by author and the credit
should be true. Getting one of these back polished into something blander is the main way
this skill disappoints.

**A theme, mood or abstraction.** "Wintery", "less thinking", "I want the room to feel like
it is breathing", "something that sounds like the building is tired". Do not transcribe
these — an abstraction turned straight into a card gives you the vague cards this app
exists to avoid. Instead let it generate a *cluster*: three or four cards that enact it by
different means, so the feeling arrives through gear, through a treatment, through a rule.
Ask yourself what he would physically do differently tonight if that word were true, and
write that. It is fine for a card to carry the theme without naming it.

**A constraint in disguise.** "Nothing quantised", "no laptop", "I keep over-arranging".
These become `constraint` cards, phrased as a removal.

**An instruction about the batch.** "Harsher than the ones I have", "keep them short", "no
reverb ones", "mostly sources". These shape every card and become none. Do not write a card
that says "be harsher".

### Allocating the count

Spread the cards across the seeds roughly in proportion to how much he said about each, but
give every distinct seed at least one card — a throwaway clause he mentioned once is still
something he wants. If the count is too small to cover every seed, cover the ones he
dwelt on and **say which seeds you left out**, so he can ask for those next. Padding a
thin batch by writing four variations of the same seed is worse than covering less.

The failure mode to watch for is averaging: twelve cards that each blend every seed into the
same beige. Each card should trace back to one seed, or two at most. A vivid phrase is the
usual culprit — it colonises the whole batch. If half your drafts orbit one word he used
once, throw them out.

When a seed is too vague to reach a concrete card, do not split the difference. Write two or
three concrete readings of it and flag them as guesses — he would rather correct a specific
card than receive a hedge.

Keep his vocabulary. If he calls something "the tube", the card says the tube.

If the whole batch shares one theme, put a tag for it in `defaults.tags` — that gives him a
draw filter for the theme afterwards, which is worth more than the tag being tidy.

## Before writing anything, get the vocabulary

Gear slugs must match character for character or the importer rejects the card, and the
inventory changes — David adds gear from the UI and flips things from wishlist to owned. So
read it from the repo rather than from memory:

```bash
node .claude/skills/sotao-cards/scripts/cards.mjs vocab
```

That prints every gear slug by category (wishlist marked `*`), the six kinds, the tags
already in use with their counts, and how big the library currently is.

Then read what already exists, so you write additions rather than restatements:

```bash
node .claude/skills/sotao-cards/scripts/cards.mjs existing
```

If the library has grown past a few hundred cards, skim the kinds you are about to write
rather than all of it. And if David has an export from the running app
(manager toolbar → export, or `GET /api/export.yaml`), prefer it — pass it as
`--existing <file>` to both `existing` and `check`. the starter set in `seed/` only knows the
starter set, so duplicate checking against it alone is optimistic.

Node is not on this machine's PATH. Prefix shell calls:
`export PATH="/c/Program Files/nodejs:$PATH"`.

## The six kinds

| Kind | It names | Drawn as |
|---|---|---|
| `idea` | a whole move, start to finish — standalone | a single draw |
| `source` | a sound: an instrument, a patch, a thing in the room | slot 1 of a spread |
| `treatment` | something done to a sound | slot 2 of a spread |
| `constraint` | a rule that removes an option | slot 3 of a spread |
| `technique` | how you play or capture it | nothing yet — a label only |
| `arrangement` | where it sits in the piece | nothing yet — a label only |

`idea` makes a card standalone; the other five make it combinable. Both at once is fine and
often best: `[idea, source]` gets drawn alone *and* fills the source slot.

**`technique` and `arrangement` are not drawable in phase 1.** A spread has exactly three
slots — source, treatment, constraint — and a single draw pulls `idea`. So a card tagged
only `[technique]` imports fine and then never surfaces. Pair those two with a drawable kind
unless David asked for labels specifically. This is the thing most likely to change in a
later phase; see the last section.

## How the draw uses what you write

The craft rules below are not taste, they follow from the selection code:

- **Every piece of gear a card names must be owned, and — with the default filter — also in
  the rack, for the card to be eligible.** A card listing four pedals needs all four patched
  tonight. So keep `gear` to what the card genuinely requires, usually one to three entries.
  A card with no gear at all is always eligible; that is why "Mono. All of it." works.
- **Wishlist gear means a card that waits.** Allowed — writing ideas ahead of a purchase is
  the point — but it will not be drawn until he buys the thing. Only reach for it if he asks
  for wishlist cards, and say which cards are parked when you do.
- **The last 30 drawn cards are suppressed.** A thin pool starts relaxing its own filters, so
  breadth beats polish: twelve different moves are worth more than twelve wordings of one.
- **Card ids are derived from the text.** Two cards differing only in case or whitespace are
  one card, and re-importing a file changes nothing. Near-duplicates are the real hazard —
  they pass validation and make the deck feel repetitive.

## Writing the cards

Second person, imperative, present tense. He is being told what to do tonight.

- **One action per card.** If it needs an "and then", it is two cards — one for the source,
  one for the treatment — and they will recombine in a spread anyway.
- **Name gear by its real name in the text**, and list the matching slug in `gear`. "Run it
  through the pitch shifter at minus twelve, wet only" is a card; "add some pitch effects" is
  a shrug. The real names come from `vocab`, which reads the seeded inventory (see below).
- **A `source` names a sound. A `treatment` names something done to a sound. A `constraint`
  removes an option.** A constraint that tells him to *do* something is an instruction
  wearing the wrong hat — "Add a tambourine in the second chorus" is not a constraint,
  "Four tracks maximum" is.
- **Length:** under 160 characters for combinable kinds, up to 300 for a standalone `idea`.
  The hard limit is 500 and hitting it means the card is a paragraph, not a prompt.
- **No genre prescriptions.** The cards suggest a move, not a style. No "make it lo-fi
  hip-hop".
- **Leave the joke out.** Dry is fine; cute wears off by the third draw.
- **`favorite` is his signal, not yours** — leave it out unless he asks for a card to be
  starred.
- **Always-on gear has no slug.** The desk, the monitors, headphones and the
  laptop are always in the chain. Name them in a card's *text* if the card is about them
  ("Nothing added after the desk") but never in `gear`.

Tags are free text and lower-cased on import. Reuse the ones already in play — they are what
his draw filters key on — and only coin a new one when nothing fits.

**Sharp:**

```
The delay with the mix pinned at 100%.                            # treatment
Kalimba, close-miked, one metre off the slab.                     # source, gear: [kalimba, condenser]
Nothing quantised.                                                # constraint
Sample yourself whistling into the toy sampler, pitch it down two
octaves, and build the bass line out of that.                     # idea, source
```

**Blunt, and why:**

```
One could experiment with delay to create ambient textures.       # third person, no gear, a genre
Use a fuzz pedal on something.                                    # which fuzz? he owns four
Sample the kalimba into the toy sampler, run it through the multi-effect, and
bounce the lot to four tracks.                                    # three moves — that is three cards
Try using only one microphone and moving it around a lot.          # a constraint that is really an instruction
```

## The file

```yaml
# sótão cards — import v1
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

  - text: Mono. All of it.
    kinds: [constraint]
    tags: [mono]
```

| Field | Required | Notes |
|---|---|---|
| `version` | yes | exactly `1` |
| `defaults.author` | no | `claude` — keep it, unless he dictated the cards himself, then `david` |
| `defaults.tags` | no | merged into every card; use it when a whole batch shares a tag |
| `cards[].text` | yes | 1–500 characters |
| `cards[].kinds` | yes | at least one |
| `cards[].gear` | no | slugs from the inventory |
| `cards[].tags` | no | lower case |
| `cards[].favorite` | no | omit by default |
| `cards[].author` | no | per-card override |

Unknown keys are rejected at every level — `kind:` instead of `kinds:` fails the card rather
than being ignored. Never write `status:`; that field exists so exports round-trip, and an
import must not resurrect or archive anything.

Write the file to `products/sotao/imports/<yyyy-mm-dd>-<slug>.yaml` (gitignored), where the slug is the theme he asked
for (`melodica`, `harsher-constraints`) or `mixed` when he gave no direction. That folder is
gitignored and easy to find from the import screen's file picker. Use block scalars (`>-`)
for anything over about 80 characters, matching the starter cards in `seed/`.

## Choosing the mix

When the input gave you seeds, the mix follows the seeds — let each one land in whatever kind
it naturally is, and do not pad the set to hit some ratio.

With no direction, write for a healthy pool — a single draw needs `idea`, a spread needs all
three of its slots filled, and the slot with the fewest cards is the one that repeats. Aim
roughly: a third `idea`, a quarter `source`, a fifth `treatment`, the rest `constraint`, with
`technique` or `arrangement` riding along as a second kind where it fits. Check what the
`vocab` output says the library is already short of and lean that way.

## Check it before you report it

```bash
node .claude/skills/sotao-cards/scripts/cards.mjs check products/sotao/imports/<file>.yaml
```

This runs the same rules the importer does — schema, unknown keys, gear slugs with
suggestions for near misses, per-card length, duplicate ids within the file and against the
existing library — and tallies the kinds.

**Errors must be zero.** Each one is a card the app would skip. Warnings are judgement:
a wishlist gear warning may be exactly what he asked for, a "probably two cards" warning
usually means split it, and an "already in the library" warning means replace that card with
something new rather than shipping a no-op row.

## Reporting back

Say how many cards, the tally per kind, and the file path. Show the cards themselves in chat
— the whole YAML in a fenced block for a dozen or fewer, otherwise the card texts grouped by
kind with the file as the source of truth. He is going to react to the wording, so make it
readable without opening the file.

When he gave you a long input, show what you heard: a one-line-per-seed map of how the count
was spent — `the toy sampler → 3 · winter → 4 · "nothing quantised" → 1 constraint · your harmonica
line → lifted as-is`. A misread seed is cheap to fix and expensive to leave buried in twenty
cards, and this is the line that surfaces it. Name the seeds you could not fit.

Flag anything else he should know: cards parked behind wishlist gear, tags you invented, a
slot still thin after this batch.

Then tell him the way in: **`/import`** in the app — paste the file or choose it, read the
preview (new / updated / unchanged / error), and commit. Re-importing the same file is a
no-op, so there is no harm in previewing twice.

## If the app has changed

This skill mirrors decisions that live in the repo. When something looks off, the source of
truth is (paths from `products/sotao/`, where the app lives):

- `seed/gear.ts` — the real gear slugs and what is owned, read at run time by
  `cards.mjs vocab`. Gear added from the app's UI lives only in the database, so if he names
  gear that is not listed, ask before inventing a slug.
- `src/lib/vocab.ts` — the kinds, authors, statuses.
- `src/lib/kinds.ts` — which kinds a draw actually reaches. If a spread gains a slot, or
  `technique` becomes drawable, fix both the kinds table above and `DRAWABLE` in
  `scripts/cards.mjs`.
- `src/lib/server/import.ts` and `src/lib/schemas.ts` — the real validation. If the schema
  gains a field (a `weight` column is the expected phase-2 change), add it to the table above
  and to `CARD_KEYS` in the script.
- `docs/IMPLEMENTATION.md` — §7 the inventory, §8 the format, §6 the draw,
  Appendix A this skill's contract.
