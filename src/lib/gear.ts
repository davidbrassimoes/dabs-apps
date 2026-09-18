import type { GearCategory } from '$lib/server/db/schema';

// Display metadata for the inventory. Types only from the schema module, so
// nothing server-side reaches the browser bundle.

/** The order the gear screen lays the categories out in, top to bottom. */
export const CATEGORY_ORDER: GearCategory[] = [
	'strings',
	'keys',
	'amps',
	'pedals',
	'mics',
	'small'
];

/**
 * The two columns the mockup draws, which split after Amps rather than at the
 * halfway row. The categories are a fixed enum of six, so this is a list, not
 * a balancing heuristic.
 */
export const CATEGORY_COLUMNS: [GearCategory[], GearCategory[]] = [
	['strings', 'keys', 'amps'],
	['pedals', 'mics', 'small']
];

/** The headings the mockup draws, which are not the enum values. */
export const CATEGORY_LABELS: Record<GearCategory, string> = {
	strings: 'Strings',
	keys: 'Keys & synths',
	amps: 'Amps',
	pedals: 'Pedals',
	mics: 'Mics & recorders',
	small: 'Small & found'
};

/** New slugs must match this (§7). */
export const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]*$/;

export function isValidSlug(slug: string): boolean {
	return SLUG_PATTERN.test(slug);
}

/** Suggests a slug from a display name, for the add-gear form. */
export function slugify(name: string): string {
	return name
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '-')
		.replace(/^-+|-+$/g, '');
}
