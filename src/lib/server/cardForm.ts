import { cardFieldsSchema, dedupe, type CardFields } from '$lib/schemas';
import { CARD_STATUSES, KINDS } from './db/schema';
import type { CardStatus, Kind } from './db/schema';
import type { GearRow } from './gear';

export type CardFormValues = {
	text: string;
	kinds: string[];
	gear: string[];
	tags: string[];
	favorite: boolean;
	author: string;
	status: string;
};

export type ParsedCardForm =
	| { ok: true; fields: CardFields; status: CardStatus; values: CardFormValues }
	| { ok: false; problem: { error: string; values: CardFormValues } };

/**
 * Reads the editor's form into validated card fields.
 *
 * Gear is checked against the inventory rather than merely against the slug
 * pattern: free text is rejected with a link to add the gear (§9), and unknown
 * slugs are a hard error on import too (§7).
 */
export async function parseCardForm(
	request: Request,
	inventory: GearRow[]
): Promise<ParsedCardForm> {
	const form = await request.formData();

	const values: CardFormValues = {
		text: String(form.get('text') ?? ''),
		kinds: form.getAll('kinds').map(String),
		gear: dedupe(
			form
				.getAll('gear')
				.map((g) => String(g).trim().toLowerCase())
				.filter(Boolean)
		),
		tags: dedupe(
			form
				.getAll('tags')
				.map((t) => String(t).trim().toLowerCase())
				.filter(Boolean)
		),
		favorite: form.get('favorite') === 'on',
		author: String(form.get('author') ?? 'david'),
		status: String(form.get('status') ?? 'active')
	};

	const known = new Set(inventory.map((g) => g.id));
	const unknown = values.gear.filter((slug) => !known.has(slug));
	if (unknown.length > 0) {
		const names = unknown.map((s) => `“${s}”`).join(', ');
		return {
			ok: false,
			problem: {
				error: `${names} ${unknown.length === 1 ? 'is' : 'are'} not in your inventory. Add ${unknown.length === 1 ? 'it' : 'them'} on the gear screen first.`,
				values
			}
		};
	}

	const parsed = cardFieldsSchema.safeParse({
		text: values.text,
		kinds: values.kinds.filter((k): k is Kind => (KINDS as readonly string[]).includes(k)),
		gear: values.gear,
		tags: values.tags,
		favorite: values.favorite,
		author: values.author
	});

	if (!parsed.success) {
		return {
			ok: false,
			problem: { error: parsed.error.issues[0]?.message ?? 'That card is not valid.', values }
		};
	}

	const status: CardStatus = (CARD_STATUSES as readonly string[]).includes(values.status)
		? (values.status as CardStatus)
		: 'active';

	return { ok: true, fields: parsed.data, status, values };
}
