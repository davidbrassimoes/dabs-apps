import { describe, expect, it } from 'vitest';
import { SEED_GEAR } from '../../seed/gear';
import { CATEGORY_LABELS, CATEGORY_ORDER, isValidSlug, slugify } from './gear';

describe('the seeded inventory', () => {
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

	it('wishlists exactly the five the spec names', () => {
		const wishlist = SEED_GEAR.filter((g) => g.owned === false).map((g) => g.id);
		expect(wishlist.sort()).toEqual(['c2-pair', 'flamenca', 'ga-r1', 'sm57', 'steel-string']);
	});

	it('has unique slugs that all match the slug pattern', () => {
		const ids = SEED_GEAR.map((g) => g.id);
		expect(new Set(ids).size).toBe(ids.length);
		for (const id of ids) expect(isValidSlug(id), id).toBe(true);
	});

	it('names no always-on gear — the desk, the monitors, the laptop (§7)', () => {
		const banned = ['ar16c', 'ar-16c', 't5v', 'adam', 'monitor', 'headphone', 'laptop'];
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
		expect(isValidSlug('sk-1')).toBe(true);
		expect(isValidSlug('rp90')).toBe(true);
		expect(isValidSlug('0-day')).toBe(true);
		expect(isValidSlug('-leading')).toBe(false);
		expect(isValidSlug('Upper')).toBe(false);
		expect(isValidSlug('has space')).toBe(false);
		expect(isValidSlug('under_score')).toBe(false);
		expect(isValidSlug('')).toBe(false);
	});

	it('suggests a slug from a display name, accents folded', () => {
		expect(slugify('Boss DD-3')).toBe('boss-dd-3');
		expect(slugify('Djembés')).toBe('djembes');
		expect(slugify('  EHX Pitch Fork+  ')).toBe('ehx-pitch-fork');
		expect(slugify('Guitarra portuguesa')).toBe('guitarra-portuguesa');
	});
});
