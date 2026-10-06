#!/usr/bin/env node
// Vocabulary and validation for generated Sótão imports.
//
// Gear slugs, kinds and the import schema are the app's business, not the
// skill's prose. The gear and the card library are read from the deployed app's
// D1 database (read-only, through wrangler), so a card set is never written
// against a stale inventory and a duplicate is caught against what is really
// in the app. Without wrangler or a network, or with --seed, it falls back to
// the repo's seed files and says so.
//
//   node .claude/skills/sotao-cards/scripts/cards.mjs vocab [--seed]
//   node .claude/skills/sotao-cards/scripts/cards.mjs existing [--seed]
//   node .claude/skills/sotao-cards/scripts/cards.mjs check <file.yaml> [--seed] [--existing <file.yaml> ...]

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";

// The skill lives at the repository root (.claude/skills/), the app it feeds in
// products/sotao/. Everything below is read from the app's folder.
const ROOT = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../../../../products/sotao",
);

const GEAR_PATH = join(ROOT, "seed", "gear.ts");
const KINDS = [
  "idea",
  "source",
  "treatment",
  "technique",
  "constraint",
  "arrangement",
];
const AUTHORS = ["david", "claude"];
const STATUSES = ["active", "archived"];
/** Kinds a draw can reach in phase 1: single pulls `idea`, a spread fills these three. */
const DRAWABLE = ["idea", "source", "treatment", "constraint"];
const TOP_KEYS = ["version", "defaults", "cards"];
const DEFAULTS_KEYS = ["author", "tags"];
const CARD_KEYS = [
  "text",
  "kinds",
  "gear",
  "tags",
  "favorite",
  "author",
  "status",
];

function fail(message) {
  console.error(`cards.mjs: ${message}`);
  process.exit(2);
}

let yamlLib;
try {
  // Resolved from the app's node_modules rather than this script's, so it is
  // the exact parser the importer uses.
  const require = createRequire(join(ROOT, "package.json"));
  yamlLib = await import(pathToFileURL(require.resolve("yaml")).href);
} catch {
  fail(
    "the `yaml` package is missing. Run `npm install` in products/sotao — this uses the same parser the app does.",
  );
}

// --- the app's own rules, mirrored ------------------------------------------

/** §5: ids are content-derived, so two texts differing only in case or spacing are one card. */
function cardId(text) {
  const normalised = text
    .normalize("NFC")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
  return createHash("sha256").update(normalised).digest("hex").slice(0, 12);
}

function distance(a, b) {
  const row = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let prev = row[0];
    row[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const swap = row[j];
      row[j] = Math.min(
        row[j] + 1,
        row[j - 1] + 1,
        prev + (a[i - 1] === b[j - 1] ? 0 : 1),
      );
      prev = swap;
    }
  }
  return row[b.length];
}

function nearest(value, candidates, max = 2) {
  let best = null;
  let bestScore = max + 1;
  for (const candidate of candidates) {
    const score = distance(value, candidate);
    if (score < bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  return bestScore <= max ? best : null;
}

// --- reading the deployed app ------------------------------------------------

const D1_NAME = "sotao";
const LIVE = `the deployed app (D1 ${D1_NAME})`;
const PRODUCTION_SQL = [
  "SELECT id, name, category, owned FROM gear ORDER BY category, sort, id",
  "SELECT c.id, c.text, (SELECT group_concat(kind, ',') FROM card_kinds WHERE card_id = c.id) AS kinds, " +
    "(SELECT group_concat(tag, ',') FROM card_tags WHERE card_id = c.id) AS tags FROM cards c",
].join("; ");

let useSeed = false;
let production; // undefined: not tried yet; null: tried and unavailable

/** Gear and cards from the deployed D1, once per run; null when it cannot be reached. */
function loadProduction() {
  if (useSeed) return null;
  if (production !== undefined) return production;
  try {
    // The app's own wrangler, run by this node: no shell, so the SQL needs no quoting.
    const wrangler = join(
      ROOT,
      "node_modules",
      "wrangler",
      "bin",
      "wrangler.js",
    );
    if (!existsSync(wrangler))
      throw new Error(
        "wrangler is not installed: run `npm install` in products/sotao",
      );
    const out = execFileSync(
      process.execPath,
      [
        wrangler,
        "d1",
        "execute",
        D1_NAME,
        "--remote",
        "--json",
        "--command",
        PRODUCTION_SQL,
      ],
      {
        cwd: ROOT,
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
        timeout: 60_000,
      },
    );
    const [gearResult, cardResult] = JSON.parse(out.slice(out.indexOf("[")));
    const split = (value) => (value ? String(value).split(",") : []);
    production = {
      gear: gearResult.results.map((g) => ({
        id: g.id,
        name: g.name,
        category: g.category,
        owned: Boolean(g.owned),
      })),
      cards: cardResult.results.map((c) => ({
        id: c.id,
        text: c.text,
        kinds: split(c.kinds),
        tags: split(c.tags),
      })),
    };
  } catch (error) {
    // With --json, wrangler reports its own errors as JSON on stdout.
    let reason;
    try {
      const report = JSON.parse(String(error?.stdout ?? "")).error;
      reason = [
        report.text,
        ...(report.notes ?? []).map((n) => n.text.split("\n")[0]),
      ].join(" ");
    } catch {
      reason =
        String(error?.stderr || error?.message || error)
          .split("\n")
          .find((line) => line.trim()) ?? "unknown error";
    }
    console.error(
      `WARNING: could not read the deployed app's D1 (${reason.trim()}).`,
    );
    console.error(
      "Falling back to the repo seed, which only knows the starter set and seeded gear.\n",
    );
    production = null;
  }
  return production;
}

// --- reading the repo -------------------------------------------------------

function loadGear() {
  const live = loadProduction();
  if (live) return live.gear;
  const path = GEAR_PATH;
  if (!existsSync(path))
    fail(`cannot find ${path}. Is products/sotao/ checked out?`);
  const source = readFileSync(path, "utf8");
  const re =
    /\{\s*id:\s*'([^']+)',\s*name:\s*'([^']+)',\s*category:\s*'([^']+)'(?:,\s*owned:\s*(false|true))?\s*\}/g;
  const rows = [...source.matchAll(re)].map((m) => ({
    id: m[1],
    name: m[2],
    category: m[3],
    owned: m[4] !== "false",
  }));
  if (rows.length === 0)
    fail(
      `read no gear out of ${short(path)} — its shape has changed, so fix this script.`,
    );
  return rows;
}

function readCards(path) {
  const doc = yamlLib.parse(readFileSync(path, "utf8"));
  const cards = Array.isArray(doc?.cards) ? doc.cards : [];
  return cards
    .filter((card) => typeof card?.text === "string")
    .map((card) => ({
      text: card.text,
      kinds: Array.isArray(card.kinds) ? card.kinds : [],
      tags: Array.isArray(card.tags) ? card.tags : [],
      id: cardId(card.text),
    }));
}

/**
 * Where the existing library comes from. An export downloaded from the app is
 * the truth; the seed only knows the starter set, so "not a duplicate"
 * judged from the seed alone is weaker than it looks.
 */
function existingSources(extra = []) {
  const sources = [];
  for (const path of extra) {
    if (!existsSync(path)) fail(`--existing file not found: ${path}`);
    sources.push(path);
  }
  if (sources.length === 0) {
    const live = loadProduction();
    if (live) return { sources: [LIVE], cards: live.cards };
  }
  if (sources.length === 0) {
    const exports = [ROOT, join(ROOT, "imports")]
      .filter((dir) => existsSync(dir))
      .flatMap((dir) =>
        readdirSync(dir)
          // `sotao-cards-` is what exports were called before the rename.
          .filter((name) => /^sotao-(cards-)?\d.*\.(yaml|yml)$/.test(name))
          .map((name) => join(dir, name)),
      )
      .sort();
    if (exports.length > 0) sources.push(exports[exports.length - 1]);
    const seed = join(ROOT, "seed", "cards.yaml");
    if (existsSync(seed)) sources.push(seed);
  }
  const cards = [];
  for (const path of sources) cards.push(...readCards(path));
  return { sources, cards };
}

const short = (path) =>
  path.replace(`${ROOT}\\`, "").replace(`${ROOT}/`, "").replaceAll("\\", "/");

// --- vocab ------------------------------------------------------------------

function vocab() {
  const gear = loadGear();
  const byCategory = new Map();
  for (const item of gear) {
    if (!byCategory.has(item.category)) byCategory.set(item.category, []);
    byCategory.get(item.category).push(item);
  }

  const from = loadProduction() ? LIVE : short(GEAR_PATH);
  console.log(
    `# gear slugs (from ${from}) —\`*\` is wishlist: writable, never drawn\n`,
  );
  for (const [category, items] of byCategory) {
    console.log(`${category}:`);
    for (const item of items)
      console.log(`  ${item.id}${item.owned ? "" : " *"}  — ${item.name}`);
  }
  const owned = gear.filter((g) => g.owned).length;
  console.log(`\n${owned} owned, ${gear.length - owned} on the wishlist.`);

  console.log(`\n# kinds\n${KINDS.join(", ")}`);
  console.log(
    `drawable in phase 1: ${DRAWABLE.join(", ")} — technique and arrangement are labels only`,
  );

  const { sources, cards } = existingSources();
  const tags = new Map();
  for (const card of cards)
    for (const tag of card.tags) tags.set(tag, (tags.get(tag) ?? 0) + 1);
  const sorted = [...tags].sort(
    (a, b) => b[1] - a[1] || a[0].localeCompare(b[0]),
  );
  console.log(`\n# tags already in use (${sorted.length}), most used first`);
  console.log(sorted.map(([tag, n]) => `${tag} (${n})`).join(", ") || "(none)");

  const kindTally = new Map();
  for (const card of cards)
    for (const kind of card.kinds)
      kindTally.set(kind, (kindTally.get(kind) ?? 0) + 1);
  console.log(`\n# existing library: ${cards.length} cards`);
  console.log(KINDS.map((k) => `${k} ${kindTally.get(k) ?? 0}`).join(" · "));
  console.log(`read from: ${sources.map(short).join(", ") || "(nothing)"}`);
}

// --- existing ---------------------------------------------------------------

function existing(extra) {
  const { sources, cards } = existingSources(extra);
  console.log(
    `# ${cards.length} existing cards, from ${sources.map(short).join(", ")}\n`,
  );
  for (const kind of KINDS) {
    const rows = cards.filter((card) => card.kinds.includes(kind));
    if (rows.length === 0) continue;
    console.log(`## ${kind} (${rows.length})`);
    for (const card of rows)
      console.log(`- ${card.text.replace(/\s+/g, " ").trim()}`);
    console.log("");
  }
}

// --- check ------------------------------------------------------------------

function check(path, extra) {
  if (!existsSync(path)) fail(`no such file: ${path}`);
  const gear = loadGear();
  const slugs = new Set(gear.map((g) => g.id));
  const wishlist = new Set(gear.filter((g) => !g.owned).map((g) => g.id));
  const { sources, cards: library } = existingSources(extra);
  const known = new Set(library.map((card) => card.id));

  const errors = [];
  const warnings = [];
  const counter = new yamlLib.LineCounter();
  const doc = yamlLib.parseDocument(readFileSync(path, "utf8"), {
    lineCounter: counter,
    strict: true,
  });

  if (doc.errors.length > 0) {
    report(
      path,
      [`YAML will not parse: ${doc.errors[0].message}`],
      [],
      null,
      sources,
    );
    return;
  }

  const lineOf = (node) =>
    node?.range ? counter.linePos(node.range[0]).line : 0;
  const keysOf = (node) =>
    (node?.items ?? []).map((pair) => String(pair.key?.value));

  for (const key of keysOf(doc.contents)) {
    if (!TOP_KEYS.includes(key))
      errors.push(
        `Unknown top-level field "${key}" — the importer rejects the whole file for this.`,
      );
  }
  if (doc.get("version") !== 1)
    errors.push("`version: 1` is required at the top level.");

  const defaults = doc.get("defaults", true);
  if (defaults) {
    for (const key of keysOf(defaults)) {
      if (!DEFAULTS_KEYS.includes(key))
        errors.push(`Unknown field "${key}" under defaults.`);
    }
    const author = doc.getIn(["defaults", "author"]);
    if (author !== undefined && !AUTHORS.includes(author)) {
      errors.push(
        `defaults.author must be ${AUTHORS.join(" or ")}, not "${author}".`,
      );
    }
  }

  const rows = doc.get("cards", true)?.items ?? [];
  if (rows.length === 0) errors.push("`cards` must hold at least one card.");

  const seenInFile = new Map();
  const kindTally = new Map();
  let newCount = 0;
  let duplicates = 0;

  rows.forEach((node, i) => {
    const n = i + 1;
    const where = `Line ${lineOf(node)} · card ${n}`;
    const card = node.toJSON();
    const add = (list, message) => list.push(`${where} — ${message}`);

    for (const key of keysOf(node)) {
      if (!CARD_KEYS.includes(key)) {
        add(
          errors,
          `unknown field "${key}". A typo like \`kind:\` for \`kinds:\` is an error, not a dropped field.`,
        );
      }
    }

    const text = typeof card?.text === "string" ? card.text.trim() : "";
    if (!text) {
      add(errors, "missing `text`.");
      return;
    }
    if (text.length > 500)
      add(errors, `text is ${text.length} characters; the hard limit is 500.`);

    const kinds = Array.isArray(card.kinds) ? card.kinds : [];
    if (kinds.length === 0)
      add(errors, "needs at least one kind, or it can never be drawn.");
    for (const kind of kinds) {
      if (!KINDS.includes(kind)) {
        const guess = nearest(String(kind), KINDS);
        add(
          errors,
          `"${kind}" is not a kind${guess ? ` — did you mean \`${guess}\`?` : "."}`,
        );
      } else {
        kindTally.set(kind, (kindTally.get(kind) ?? 0) + 1);
      }
    }
    if (new Set(kinds).size !== kinds.length) add(warnings, "repeats a kind.");
    if (kinds.length > 0 && !kinds.some((kind) => DRAWABLE.includes(kind))) {
      add(
        warnings,
        `only ${kinds.join(" + ")} — no draw reaches this card in phase 1. Add a drawable kind unless that is deliberate.`,
      );
    }

    const cardGear = Array.isArray(card.gear) ? card.gear : [];
    for (const slug of cardGear) {
      if (!/^[a-z0-9][a-z0-9-]*$/.test(String(slug))) {
        add(
          errors,
          `gear slug "${slug}" is not lower-case letters, digits and hyphens.`,
        );
      } else if (!slugs.has(slug)) {
        const guess = nearest(String(slug), [...slugs]);
        add(
          errors,
          `gear \`${slug}\` is not in the inventory${guess ? ` — did you mean \`${guess}\`?` : "."} This card would be skipped.`,
        );
      } else if (wishlist.has(slug)) {
        add(
          warnings,
          `\`${slug}\` is wishlist gear, so this card imports but is never drawn until it is owned.`,
        );
      }
    }
    if (new Set(cardGear).size !== cardGear.length)
      add(warnings, "lists the same gear twice.");
    if (cardGear.length > 3) {
      add(
        warnings,
        `${cardGear.length} pieces of gear — all of them must be owned and in the rack for the card to be drawn, so it is rare by construction.`,
      );
    }

    const tags = Array.isArray(card.tags) ? card.tags : [];
    for (const tag of tags) {
      if (typeof tag !== "string" || tag.trim().length === 0)
        add(errors, "has an empty tag.");
      else if (tag.length > 40)
        add(errors, `tag "${tag}" is longer than 40 characters.`);
      else if (tag !== tag.toLowerCase())
        add(warnings, `tag "${tag}" will be lower-cased on import.`);
    }

    if (card.favorite !== undefined && typeof card.favorite !== "boolean")
      add(errors, "`favorite` must be true or false.");
    if (card.author !== undefined && !AUTHORS.includes(card.author))
      add(errors, `author must be ${AUTHORS.join(" or ")}.`);
    if (card.status !== undefined && !STATUSES.includes(card.status))
      add(errors, `status must be ${STATUSES.join(" or ")}.`);

    const limit = kinds.includes("idea") ? 300 : 160;
    if (text.length > limit) {
      const shape = kinds.includes("idea")
        ? "a standalone idea"
        : "a combinable card";
      add(
        warnings,
        `${text.length} characters against a target of ${limit} for ${shape}. Probably two cards.`,
      );
    }

    const id = cardId(text);
    if (seenInFile.has(id)) {
      add(
        errors,
        `the same card as card ${seenInFile.get(id)} once case and spacing are normalised — one of them is dead weight.`,
      );
    } else {
      seenInFile.set(id, n);
      if (known.has(id)) {
        duplicates++;
        add(
          warnings,
          "is already in the library — it imports as Unchanged and adds nothing.",
        );
      } else {
        newCount++;
      }
    }
  });

  report(
    path,
    errors,
    warnings,
    { rows: rows.length, newCount, duplicates, kindTally },
    sources,
  );
}

function report(path, errors, warnings, tally, sources) {
  for (const message of errors) console.log(`error  ${message}`);
  for (const message of warnings) console.log(`warn   ${message}`);
  if (errors.length === 0 && warnings.length === 0)
    console.log("Clean — no errors, no warnings.");

  if (tally) {
    console.log(
      `\n${short(path)}: ${tally.rows} cards — ${tally.newCount} new, ${tally.duplicates} already in the library.`,
    );
    console.log(
      KINDS.map((k) => `${k} ${tally.kindTally.get(k) ?? 0}`).join(" · "),
    );
    console.log(
      `checked against: ${sources.map(short).join(", ") || "(no existing cards found)"}`,
    );
  }
  console.log(`\n${errors.length} error(s), ${warnings.length} warning(s).`);
  if (errors.length > 0) process.exit(1);
}

// --- dispatch ---------------------------------------------------------------

const [command, ...rest] = process.argv.slice(2);
const extra = [];
const positional = [];
for (let i = 0; i < rest.length; i++) {
  if (rest[i] === "--existing") extra.push(rest[++i]);
  else if (rest[i] === "--seed") useSeed = true;
  else positional.push(rest[i]);
}

if (command === "vocab") vocab();
else if (command === "existing") existing(extra);
else if (command === "check") {
  if (!positional[0])
    fail("check needs a file — cards.mjs check imports/whatever.yaml");
  check(positional[0], extra);
} else {
  fail(
    "usage: cards.mjs vocab | existing | check <file>   [--seed] [--existing <file>]",
  );
}
