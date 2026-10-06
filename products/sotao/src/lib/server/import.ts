import { LineCounter, parseDocument, type Document } from 'yaml';
import { z } from 'zod';
import { eq } from 'drizzle-orm';
import type { BatchItem } from 'drizzle-orm/batch';
import { db } from './db';
import { cardGear, cardKinds, cardTags, cards, gear } from './db/schema';
import { cardId } from '$lib/cardId';
import { nearest } from '$lib/levenshtein';
import { cardTextSchema, tagSchema } from '$lib/schemas';
import { AUTHORS, CARD_STATUSES, KINDS } from '$lib/vocab';
import type { Author, CardStatus, Kind } from '$lib/vocab';

// YAML parse → validate → diff (§8).
//
// parseImport is pure with respect to the database: everything it needs about
// the inventory and the existing cards is passed in as plain data, so it is
// testable with fixtures the way §14 asks for import.ts. The caller (the
// preview and commit routes) is what actually queries the database and, on
// commit, writes the result.

export type ExistingCardSnapshot = {
	kinds: Kind[];
	gear: string[];
	tags: string[];
	favorite: boolean;
};

export type ImportRowClass = 'new' | 'updated' | 'unchanged' | 'error';

export type ImportIssue = {
	line: number;
	cardIndex: number;
	message: string;
};

export type ImportRow = {
	/** 1-based position among the file's cards, matching the "card N" in messages. */
	index: number;
	line: number;
	class: ImportRowClass;
	/** null only on an 'error' row whose text could not be read at all. */
	id: string | null;
	text: string | null;
	kinds: Kind[];
	gear: string[];
	tags: string[];
	favorite: boolean;
	author: Author | null;
	/**
	 * Exported YAML carries this so an archived card round-trips (§8's export
	 * section). It only ever applies to a brand new card — an import never
	 * changes status on one that already exists.
	 */
	status: CardStatus;
	/** Which fields differ from the existing card, for an 'updated' row. */
	changed: ('kinds' | 'gear' | 'tags' | 'favorite')[];
	issue: ImportIssue | null;
};

export type ImportReport = {
	rows: ImportRow[];
	counts: { new: number; updated: number; unchanged: number; error: number };
	/** Set when the whole file could not be trusted — nothing was diffed. */
	fileError: string | null;
};

type ParseContext = {
	knownGear: ReadonlySet<string>;
	existingCards: ReadonlyMap<string, ExistingCardSnapshot>;
};

const TOP_LEVEL_KEYS = new Set(['version', 'defaults', 'cards']);
const DEFAULTS_KEYS = new Set(['author', 'tags']);
const CARD_KEYS = new Set(['text', 'kinds', 'gear', 'tags', 'favorite', 'author', 'status']);

const cardRowSchema = z.object({
	text: cardTextSchema,
	kinds: z.array(z.enum(KINDS)).min(1, 'Pick at least one kind, or the card can never be drawn.'),
	gear: z.array(z.string()).default([]),
	tags: z.array(tagSchema).default([]),
	favorite: z.boolean().default(false),
	author: z.enum(AUTHORS).optional(),
	status: z.enum(CARD_STATUSES).optional()
});

function sameSet(a: string[], b: string[]): boolean {
	if (a.length !== b.length) return false;
	const sorted = [...b].sort();
	return [...a].sort().every((value, i) => value === sorted[i]);
}

function keysOf(nodeMap: unknown): string[] {
	const map = nodeMap as { items?: { key: { value: unknown } }[] } | null;
	if (!map?.items) return [];
	return map.items.map((pair) => String(pair.key.value));
}

function unknownKeys(nodeMap: unknown, allowed: Set<string>): string[] {
	return keysOf(nodeMap).filter((key) => !allowed.has(key));
}

export function parseImport(yamlText: string, context: ParseContext): ImportReport {
	const empty: ImportReport = {
		rows: [],
		counts: { new: 0, updated: 0, unchanged: 0, error: 0 },
		fileError: null
	};

	const lineCounter = new LineCounter();
	const doc: Document = parseDocument(yamlText, { lineCounter, strict: true });

	if (doc.errors.length > 0) {
		return { ...empty, fileError: doc.errors[0].message };
	}

	const rootUnknown = unknownKeys(doc.contents, TOP_LEVEL_KEYS);
	if (rootUnknown.length > 0) {
		return { ...empty, fileError: `Unknown field "${rootUnknown[0]}" at the top level.` };
	}

	const version = doc.get('version');
	if (version !== 1) {
		return { ...empty, fileError: `version must be 1 (got ${JSON.stringify(version)}).` };
	}

	const defaultsNode = doc.get('defaults', true);
	if (defaultsNode !== undefined) {
		const badKeys = unknownKeys(defaultsNode, DEFAULTS_KEYS);
		if (badKeys.length > 0) {
			return { ...empty, fileError: `Unknown field "${badKeys[0]}" in defaults.` };
		}
	}

	const plain = doc.toJS() as {
		defaults?: { author?: string; tags?: string[] };
		cards?: unknown;
	};

	const defaultAuthor: Author = (AUTHORS as readonly string[]).includes(
		plain.defaults?.author ?? ''
	)
		? (plain.defaults!.author as Author)
		: 'claude';
	const defaultTags = plain.defaults?.tags ?? [];

	const cardsNode = doc.get('cards', true);
	if (!Array.isArray(plain.cards) || plain.cards.length === 0) {
		return { ...empty, fileError: 'cards must be a non-empty array.' };
	}
	const cardList = plain.cards;

	const seq = cardsNode as { items: unknown[] };
	const rows: ImportRow[] = [];
	const seenInFile = new Set<string>();

	seq.items.forEach((itemNode, i) => {
		const index = i + 1;
		const line = lineCounter.linePos(
			(itemNode as { range: [number, number, number] }).range[0]
		).line;
		const raw = cardList[i] as Record<string, unknown>;

		const badKeys = unknownKeys(itemNode, CARD_KEYS);
		if (badKeys.length > 0) {
			rows.push(
				errorRow(
					index,
					line,
					`Unknown field "${badKeys[0]}" — did you mean one of ${[...CARD_KEYS].join(', ')}?`
				)
			);
			return;
		}

		const parsed = cardRowSchema.safeParse(raw);
		if (!parsed.success) {
			rows.push(
				errorRow(index, line, parsed.error.issues[0]?.message ?? 'This card is not valid.')
			);
			return;
		}

		const gearIssue = parsed.data.gear.find((slug) => !context.knownGear.has(slug));
		if (gearIssue) {
			const suggestion = nearest(gearIssue, context.knownGear);
			const message = suggestion
				? `Gear "${gearIssue}" is not in your inventory. Did you mean "${suggestion}"? Fix the file, or add the gear first — this card will be skipped.`
				: `Gear "${gearIssue}" is not in your inventory. Add it first — this card will be skipped.`;
			// The line the gear array itself sits on, not the card's first line.
			const gearPair = (
				itemNode as {
					items: { key: { value: string }; value: { range: [number, number, number] } }[];
				}
			).items.find((p) => p.key.value === 'gear');
			const gearLine = gearPair ? lineCounter.linePos(gearPair.value.range[0]).line : line;
			rows.push(errorRow(index, gearLine, message));
			return;
		}

		const text = parsed.data.text;
		const id = cardId(text);

		if (seenInFile.has(id)) {
			rows.push(
				errorRow(
					index,
					line,
					'Two cards in this file have the same text — duplicates are not allowed.'
				)
			);
			return;
		}
		seenInFile.add(id);

		const kinds = [...new Set(parsed.data.kinds)] as Kind[];
		const gear = [...new Set(parsed.data.gear)].sort();
		const tags = [...new Set([...defaultTags, ...parsed.data.tags])].sort();
		const favorite = parsed.data.favorite;
		const author = parsed.data.author ?? defaultAuthor;
		const status: CardStatus = parsed.data.status ?? 'active';

		const existing = context.existingCards.get(id);

		if (!existing) {
			rows.push({
				index,
				line,
				class: 'new',
				id,
				text,
				kinds,
				gear,
				tags,
				favorite,
				author,
				status,
				changed: [],
				issue: null
			});
			return;
		}

		const changed: ImportRow['changed'] = [];
		if (!sameSet(existing.kinds, kinds)) changed.push('kinds');
		if (!sameSet(existing.gear, gear)) changed.push('gear');
		if (!sameSet(existing.tags, tags)) changed.push('tags');
		if (existing.favorite !== favorite) changed.push('favorite');

		rows.push({
			index,
			line,
			// Author and status are never compared: an import never changes them
			// on a card that already exists (§8).
			class: changed.length > 0 ? 'updated' : 'unchanged',
			id,
			text,
			kinds,
			gear,
			tags,
			favorite,
			author,
			status,
			changed,
			issue: null
		});
	});

	const counts = {
		new: rows.filter((r) => r.class === 'new').length,
		updated: rows.filter((r) => r.class === 'updated').length,
		unchanged: rows.filter((r) => r.class === 'unchanged').length,
		error: rows.filter((r) => r.class === 'error').length
	};

	return { rows, counts, fileError: null };
}

function errorRow(index: number, line: number, message: string): ImportRow {
	return {
		index,
		line,
		class: 'error',
		id: null,
		text: null,
		kinds: [],
		gear: [],
		tags: [],
		favorite: false,
		author: null,
		status: 'active',
		changed: [],
		issue: { line, cardIndex: index, message }
	};
}

// The database-facing half: what the preview and commit routes actually call.
// Everything above this line is pure and covered by import.test.ts.

/** The gear vocabulary the importer checks slugs against — every slug, wishlist included. */
export async function knownGearSlugs(): Promise<Set<string>> {
	const rows = await db.select({ id: gear.id }).from(gear);
	return new Set(rows.map((r) => r.id));
}

/**
 * Every card, any status, keyed by id, with just enough to diff against. All
 * statuses matter here: an archived card must still be found by its id, or a
 * re-imported export would try to insert it as new and collide on the
 * primary key instead of correctly reporting Unchanged (§8).
 */
export async function existingCardSnapshots(): Promise<Map<string, ExistingCardSnapshot>> {
	const [cardRows, kindRows, gearRows, tagRows] = await Promise.all([
		db.select({ id: cards.id, favorite: cards.favorite }).from(cards),
		db.select().from(cardKinds),
		db.select().from(cardGear),
		db.select().from(cardTags)
	]);

	const push = <T>(map: Map<string, T[]>, key: string, value: T) => {
		const list = map.get(key);
		if (list) list.push(value);
		else map.set(key, [value]);
	};

	const kindsBy = new Map<string, Kind[]>();
	const gearBy = new Map<string, string[]>();
	const tagsBy = new Map<string, string[]>();
	for (const row of kindRows) push(kindsBy, row.cardId, row.kind);
	for (const row of gearRows) push(gearBy, row.cardId, row.gearId);
	for (const row of tagRows) push(tagsBy, row.cardId, row.tag);

	const snapshots = new Map<string, ExistingCardSnapshot>();
	for (const row of cardRows) {
		snapshots.set(row.id, {
			kinds: kindsBy.get(row.id) ?? [],
			gear: gearBy.get(row.id) ?? [],
			tags: tagsBy.get(row.id) ?? [],
			favorite: row.favorite
		});
	}
	return snapshots;
}

export type CommitSummary = { created: number; updated: number };

/**
 * Writes the New and Updated rows from a report, in one atomic batch — nothing
 * is written before this runs, and either all of it lands or none of it does.
 *
 * An Updated row touches only kinds, gear, tags and favorite. Author, status,
 * draw_count, last_drawn_at and created_at are left exactly as they were: an
 * import never changes them on a card that already exists (§8).
 */
export async function applyImport(rows: ImportRow[]): Promise<CommitSummary> {
	const toCreate = rows.filter((r) => r.class === 'new');
	const toUpdate = rows.filter((r) => r.class === 'updated');

	const now = Date.now();
	// One db.batch: atomic on both drivers (D1 has no transactions), so a failing
	// statement leaves nothing written. Order matters: a card before its joins.
	const statements: BatchItem<'sqlite'>[] = [];

	const joins = (row: ImportRow) => {
		if (row.kinds.length) {
			statements.push(
				db.insert(cardKinds).values(row.kinds.map((kind) => ({ cardId: row.id!, kind })))
			);
		}
		if (row.gear.length) {
			statements.push(
				db.insert(cardGear).values(row.gear.map((gearId) => ({ cardId: row.id!, gearId })))
			);
		}
		if (row.tags.length) {
			statements.push(
				db.insert(cardTags).values(row.tags.map((tag) => ({ cardId: row.id!, tag })))
			);
		}
	};

	for (const row of toCreate) {
		if (!row.id || !row.text || !row.author) continue;

		statements.push(
			db.insert(cards).values({
				id: row.id,
				text: row.text,
				favorite: row.favorite,
				status: row.status,
				author: row.author,
				drawCount: 0,
				lastDrawnAt: null,
				createdAt: now,
				updatedAt: now
			})
		);
		joins(row);
	}

	for (const row of toUpdate) {
		if (!row.id) continue;

		statements.push(
			db.update(cards).set({ favorite: row.favorite, updatedAt: now }).where(eq(cards.id, row.id)),
			db.delete(cardKinds).where(eq(cardKinds.cardId, row.id)),
			db.delete(cardGear).where(eq(cardGear.cardId, row.id)),
			db.delete(cardTags).where(eq(cardTags.cardId, row.id))
		);
		joins(row);
	}

	if (statements.length > 0) {
		await db.batch(statements as [BatchItem<'sqlite'>, ...BatchItem<'sqlite'>[]]);
	}

	return { created: toCreate.length, updated: toUpdate.length };
}
