import { SEED_GEAR } from './gear';
import starterCardsYaml from './cards.yaml?raw';

// What an empty database is seeded with: the real rig and the starter cards.
// Seeding only runs on empty tables.

export { SEED_GEAR };

export const STARTER_CARDS_YAML: string = starterCardsYaml;

/** Which files the seed came from, for error messages. */
export const SEED_SOURCE = { gear: 'seed/gear.ts', cards: 'seed/cards.yaml' };
