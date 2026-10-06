import { describe, expect, it } from 'vitest';
import { EXAMPLE_GEAR, SEED_GEAR } from '../../seed/index';
import { CATEGORY_LABELS, CATEGORY_ORDER, isValidSlug, slugify } from './gear';

// Two inventories: the committed example, always, and the real one when
// seed/gear.local.ts is on this machine (it is gitignored, ADR-027). The example
// mirrors §7 row for row, so §7's shape is pinned on it; the rules that make an
// inventory valid are checked on both.
const INVENTORIES: [string, typeof SEED_GEAR][] =
	SEED_GEAR === EXAMPLE_GEAR
		? [['example', EXAMPLE_GEAR]]
		: [
				['example', EXAMPLE_GEAR],
				['local', SEED_GEAR]
			];

describe.each(INVENTORIES)('the seeded inventory (%s)', (_, SEED_GEAR) => {
	it('is 47 rows: 42 owned and 5 on the wishlist', () => {
		expect(SEED_GEAR).toHaveLength(47);
		expect(SEED_GEAR.filter((g) => g.owned !== false)).toHaveLength(42);
		expect(SEED_GEAR.filter((g) => g.owned === false)).toHaveLength(5);
	});

	it('holds the per-category counts from §7', () => {
		const counts = Object.fromEntries(
			CATEGORY_ORDER.map((c) => [c, SEED_GEAR.filter((g) => g.category === c).length])
		);
		expect(counts).toEqual({ strings: 10, keys: 7, amps: 3, pedals: 14, mics: 6, small: 7 });
	});

	it('wishlists five, as the spec does: three mics and two strings', () => {
		const wishlist = SEED_GEAR.filter((g) => g.owned === false).map((g) => g.id);
		expect(wishlist).toHaveLength(5);
		expect(SEED_GEAR.filter((g) => g.category === 'mics' && g.owned === false)).toHaveLength(3);
		expect(SEED_GEAR.filter((g) => g.category === 'strings' && g.owned === false)).toHaveLength(2);
	});

	it('has unique slugs that all match the slug pattern', () => {
		const ids = SEED_GEAR.map((g) => g.id);
		expect(new Set(ids).size).toBe(ids.length);
		for (const id of ids) expect(isValidSlug(id), id).toBe(true);
	});

	it('names no always-on gear — the desk, the monitors, the laptop (§7)', () => {
		const banned = ['desk', 'monitor', 'headphone', 'laptop'];
		const haystack = SEED_GEAR.map((g) => `${g.id} ${g.name}`.toLowerCase());
		for (const word of banned) {
			expect(
				haystack.some((entry) => entry.includes(word)),
				word
			).toBe(false);
		}
	});

	it('labels every category the screen lays out', () => {
		for (const category of CATEGORY_ORDER) expect(CATEGORY_LABELS[category]).toBeTruthy();
		expect(new Set(SEED_GEAR.map((g) => g.category))).toEqual(new Set(CATEGORY_ORDER));
	});
});

describe('slug rules', () => {
	it('accepts the shapes §7 allows and rejects the rest', () => {
		expect(isValidSlug('toy-1')).toBe(true);
		expect(isValidSlug('fx90')).toBe(true);
		expect(isValidSlug('0-day')).toBe(true);
		expect(isValidSlug('-leading')).toBe(false);
		expect(isValidSlug('Upper')).toBe(false);
		expect(isValidSlug('has space')).toBe(false);
		expect(isValidSlug('under_score')).toBe(false);
		expect(isValidSlug('')).toBe(false);
	});

	it('suggests a slug from a display name, accents folded', () => {
		expect(slugify('Compact Delay DL-3')).toBe('compact-delay-dl-3');
		expect(slugify('Djembés')).toBe('djembes');
		expect(slugify('  Pitch Shifter+  ')).toBe('pitch-shifter');
		expect(slugify('Guitarra portuguesa')).toBe('guitarra-portuguesa');
	});
});
