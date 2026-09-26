import type { SeedGear } from './types';
import { SEED_GEAR as EXAMPLE_GEAR } from './gear.example';
import exampleCardsYaml from './cards.example.yaml?raw';

// What an empty database is seeded with. Each half has two files:
//
//   gear.local.ts, cards.local.yaml       the real rig and starter cards —
//                                         gitignored, on DABS's machine only
//   gear.example.ts, cards.example.yaml   the same, described generically —
//                                         committed
//
// Real product and manufacturer names stay out of the repository (rule H1,
// ADR-017), and the real inventory is nothing but those names. The local file
// wins when it exists. Otherwise — CI, a fresh clone — the example is used.
//
// Resolved at build time by Vite's import.meta.glob, so a deployed server
// carries whichever files were present on the machine that built it. That
// matters once: seeding only runs on empty tables.

const localGear = import.meta.glob<SeedGear[]>('./gear.local.ts', {
	eager: true,
	import: 'SEED_GEAR'
});
const localCards = import.meta.glob<string>('./cards.local.yaml', {
	eager: true,
	query: '?raw',
	import: 'default'
});

export { EXAMPLE_GEAR, exampleCardsYaml };

export const SEED_GEAR: SeedGear[] = localGear['./gear.local.ts'] ?? EXAMPLE_GEAR;

export const STARTER_CARDS_YAML: string = localCards['./cards.local.yaml'] ?? exampleCardsYaml;

/** Which files the seed came from, for error messages. */
export const SEED_SOURCE = {
	gear: localGear['./gear.local.ts'] ? 'seed/gear.local.ts' : 'seed/gear.example.ts',
	cards: localCards['./cards.local.yaml'] ? 'seed/cards.local.yaml' : 'seed/cards.example.yaml'
};
