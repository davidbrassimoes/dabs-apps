import { KINDS, type Kind } from '$lib/vocab';

// Kind vocabulary and what a combination of kinds actually means for drawing.
// Types only from the schema module, so nothing server-side reaches the client.

export const ALL_KINDS: readonly Kind[] = KINDS;

/**
 * The three slots a spread fills, in order (§6). Fixed in phase 1 — configurable
 * recipes are phase 2, and that is the only thing that changes here.
 */
export const SPREAD_SLOTS = ['source', 'treatment', 'constraint'] as const;
export type SpreadSlot = (typeof SPREAD_SLOTS)[number];

/** The kind a single draw pulls from (§6). */
export const SINGLE_KIND: Kind = 'idea';

export function isSpreadSlot(kind: Kind): kind is SpreadSlot {
	return (SPREAD_SLOTS as readonly string[]).includes(kind);
}

/**
 * The sentence under the kind checkboxes (§9).
 *
 * It tells the truth about phase 1 rather than repeating the mockup's copy:
 * a spread has exactly three slots — source, treatment and constraint — so
 * `technique` and `arrangement` do not place a card in a draw yet. Saying
 * otherwise would promise a draw that never comes.
 */
export function describeKinds(kinds: Kind[]): string {
	if (kinds.length === 0) return 'Not drawable yet — pick at least one.';

	const standalone = kinds.includes(SINGLE_KIND);
	const slots = SPREAD_SLOTS.filter((slot) => kinds.includes(slot));
	const inert = kinds.filter((kind) => kind !== SINGLE_KIND && !isSpreadSlot(kind));

	const parts: string[] = [];

	if (standalone && slots.length > 0) {
		parts.push(`Drawn on its own, and into the ${list(slots)} slot${slots.length > 1 ? 's' : ''}.`);
	} else if (standalone) {
		parts.push('Drawn on its own.');
	} else if (slots.length > 0) {
		parts.push(`Drawn into spreads only — the ${list(slots)} slot${slots.length > 1 ? 's' : ''}.`);
	}

	if (inert.length > 0) {
		const names = list(inert);
		const verb = inert.length > 1 ? 'are' : 'is';
		parts.push(
			parts.length === 0
				? `Not drawable yet: ${names} ${verb} a label in phase 1, with no slot in a spread. Add idea to draw it on its own.`
				: `${capitalise(names)} ${verb} a label only — no slot in a spread yet.`
		);
	}

	return parts.join(' ');
}

function list(values: readonly string[]): string {
	if (values.length <= 1) return values[0] ?? '';
	return `${values.slice(0, -1).join(', ')} and ${values[values.length - 1]}`;
}

function capitalise(value: string): string {
	return value.charAt(0).toUpperCase() + value.slice(1);
}
