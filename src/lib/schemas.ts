import { z } from 'zod';
import { AUTHORS, CARD_STATUSES, DRAW_MODES, KINDS } from '$lib/vocab';

// One schema module shared by the API and the YAML importer (§3), so a card
// written in the editor and a card pasted into /import are held to the same
// rules. The importer adds the file-level wrapper in M4.

export const kindSchema = z.enum(KINDS);
export const authorSchema = z.enum(AUTHORS);
export const statusSchema = z.enum(CARD_STATUSES);

/** §8's table: 1–500 characters, after trimming. */
export const cardTextSchema = z
	.string()
	.trim()
	.min(1, 'A card needs some text.')
	.max(500, 'A card is at most 500 characters.');

/** Gear is referenced by slug; the slug must exist in the inventory (§7). */
export const gearSlugSchema = z
	.string()
	.trim()
	.regex(/^[a-z0-9][a-z0-9-]*$/, 'Gear slugs are lower-case letters, digits and hyphens.');

/** Tags are free text, but normalised so "Room" and "room" are one tag. */
export const tagSchema = z
	.string()
	.trim()
	.min(1)
	.max(40)
	.transform((t) => t.toLowerCase());

/** Saving with zero kinds is blocked — the card would never be drawable (§9). */
export const kindsSchema = z
	.array(kindSchema)
	.min(1, 'Pick at least one kind, or the card can never be drawn.');

/**
 * The fields a card carries, shared by the editor and the importer. `id` is
 * absent on purpose: it is derived from `text` and assigned once (§5).
 */
export const cardFieldsSchema = z.object({
	text: cardTextSchema,
	kinds: kindsSchema,
	gear: z.array(gearSlugSchema).default([]),
	tags: z.array(tagSchema).default([]),
	favorite: z.boolean().default(false),
	author: authorSchema
});

export type CardFields = z.infer<typeof cardFieldsSchema>;

/**
 * A completed draw, as the client posts it (§6). The uuid and drawnAt are
 * minted client-side at draw time so a replay carries the same identity.
 */
export const drawRecordSchema = z.object({
	uuid: z.string().uuid('A draw needs a client-minted uuid.'),
	drawnAt: z.number().int().positive(),
	mode: z.enum(DRAW_MODES),
	cards: z
		.array(
			z.object({
				slot: z.number().int().min(0).max(2),
				slotKind: kindSchema.nullable(),
				cardId: z.string().regex(/^[0-9a-f]{12}$/, 'Card ids are twelve hex characters.')
			})
		)
		.min(1)
		.max(3)
});

/** Unique, order-preserving, for the lists that come off a form. */
export function dedupe(values: string[]): string[] {
	return [...new Set(values)];
}
