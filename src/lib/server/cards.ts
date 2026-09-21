import { and, eq, inArray, sql } from 'drizzle-orm';
import { db } from './db';
import { cardGear, cardKinds, cardTags, cards, gear } from './db/schema';
import type { Author, CardStatus, Kind } from './db/schema';
import { cardId } from '$lib/cardId';
import type { CardFields } from '$lib/schemas';

export type CardRow = {
	id: string;
	text: string;
	favorite: boolean;
	status: CardStatus;
	author: Author;
	drawCount: number;
	lastDrawnAt: number | null;
	createdAt: number;
	updatedAt: number;
	kinds: Kind[];
	gear: string[];
	tags: string[];
	/** True when any gear this card names is not owned — the row renders dimmed. */
	hasUnownedGear: boolean;
};

type Joined = {
	kinds: Map<string, Kind[]>;
	gear: Map<string, string[]>;
	tags: Map<string, string[]>;
	unowned: Set<string>;
};

/**
 * Loads the joins for a set of cards in three queries rather than one per row.
 * A few hundred cards is small, but N+1 over a table view is still silly.
 */
async function loadJoins(ids: string[]): Promise<Joined> {
	const empty: Joined = { kinds: new Map(), gear: new Map(), tags: new Map(), unowned: new Set() };
	if (ids.length === 0) return empty;

	const [kindRows, gearRows, tagRows] = await Promise.all([
		db.select().from(cardKinds).where(inArray(cardKinds.cardId, ids)),
		db
			.select({ cardId: cardGear.cardId, gearId: cardGear.gearId, owned: gear.owned })
			.from(cardGear)
			.innerJoin(gear, eq(cardGear.gearId, gear.id))
			.where(inArray(cardGear.cardId, ids)),
		db.select().from(cardTags).where(inArray(cardTags.cardId, ids))
	]);

	const push = <T>(map: Map<string, T[]>, key: string, value: T) => {
		const list = map.get(key);
		if (list) list.push(value);
		else map.set(key, [value]);
	};

	for (const row of kindRows) push(empty.kinds, row.cardId, row.kind);
	for (const row of tagRows) push(empty.tags, row.cardId, row.tag);
	for (const row of gearRows) {
		push(empty.gear, row.cardId, row.gearId);
		if (!row.owned) empty.unowned.add(row.cardId);
	}

	return empty;
}

function assemble(
	rows: (typeof cards.$inferSelect)[],
	joins: Joined,
	kindOrder: readonly Kind[]
): CardRow[] {
	return rows.map((row) => ({
		...row,
		kinds: (joins.kinds.get(row.id) ?? []).sort(
			(a, b) => kindOrder.indexOf(a) - kindOrder.indexOf(b)
		),
		gear: (joins.gear.get(row.id) ?? []).sort(),
		tags: (joins.tags.get(row.id) ?? []).sort(),
		hasUnownedGear: joins.unowned.has(row.id)
	}));
}

export type CardFilters = {
	search: string;
	kinds: Kind[];
	author: Author | null;
	status: CardStatus | null;
	gear: string | null;
	favouritesOnly: boolean;
};

export const EMPTY_FILTERS: CardFilters = {
	search: '',
	kinds: [],
	author: null,
	status: 'active',
	gear: null,
	favouritesOnly: false
};

export async function listCards(
	filters: CardFilters,
	kindOrder: readonly Kind[]
): Promise<CardRow[]> {
	const where = [];

	if (filters.search.trim()) {
		// Substring, case-insensitive, over card text (§9).
		const needle = `%${filters.search.trim().toLowerCase()}%`;
		where.push(sql`lower(${cards.text}) LIKE ${needle}`);
	}
	if (filters.status) where.push(eq(cards.status, filters.status));
	if (filters.author) where.push(eq(cards.author, filters.author));
	if (filters.favouritesOnly) where.push(eq(cards.favorite, true));

	// Multi-select kinds: a card matching any selected kind.
	if (filters.kinds.length > 0) {
		where.push(
			sql`EXISTS (SELECT 1 FROM ${cardKinds} WHERE ${cardKinds.cardId} = ${cards.id} AND ${cardKinds.kind} IN ${filters.kinds})`
		);
	}
	if (filters.gear) {
		where.push(
			sql`EXISTS (SELECT 1 FROM ${cardGear} WHERE ${cardGear.cardId} = ${cards.id} AND ${cardGear.gearId} = ${filters.gear})`
		);
	}

	const rows = await db
		.select()
		.from(cards)
		.where(where.length ? and(...where) : undefined)
		// Default sort: least recently drawn first, never-drawn at the top (§9).
		.orderBy(sql`${cards.lastDrawnAt} IS NOT NULL`, sql`${cards.lastDrawnAt} ASC`, cards.createdAt);

	return assemble(rows, await loadJoins(rows.map((r) => r.id)), kindOrder);
}

export async function getCard(id: string, kindOrder: readonly Kind[]): Promise<CardRow | null> {
	const [row] = await db.select().from(cards).where(eq(cards.id, id)).limit(1);
	if (!row) return null;
	return assemble([row], await loadJoins([row.id]), kindOrder)[0];
}

/** Counts per kind for the filter chips, over active cards. */
export async function kindCounts(): Promise<Record<string, number>> {
	const rows = await db
		.select({ kind: cardKinds.kind, count: sql<number>`count(*)` })
		.from(cardKinds)
		.innerJoin(cards, eq(cards.id, cardKinds.cardId))
		.where(eq(cards.status, 'active'))
		.groupBy(cardKinds.kind);
	return Object.fromEntries(rows.map((r) => [r.kind, Number(r.count)]));
}

/** Every tag in use, for the editor's suggestions. */
export async function allTags(): Promise<string[]> {
	const rows = await db.selectDistinct({ tag: cardTags.tag }).from(cardTags).orderBy(cardTags.tag);
	return rows.map((r) => r.tag);
}

async function writeJoins(id: string, fields: CardFields): Promise<void> {
	await db.delete(cardKinds).where(eq(cardKinds.cardId, id));
	await db.delete(cardGear).where(eq(cardGear.cardId, id));
	await db.delete(cardTags).where(eq(cardTags.cardId, id));

	if (fields.kinds.length) {
		await db.insert(cardKinds).values(fields.kinds.map((kind) => ({ cardId: id, kind })));
	}
	if (fields.gear.length) {
		await db.insert(cardGear).values(fields.gear.map((gearId) => ({ cardId: id, gearId })));
	}
	if (fields.tags.length) {
		await db.insert(cardTags).values(fields.tags.map((tag) => ({ cardId: id, tag })));
	}
}

export async function createCard(fields: CardFields): Promise<string> {
	const id = cardId(fields.text);
	const now = Date.now();

	await db.insert(cards).values({
		id,
		text: fields.text,
		favorite: fields.favorite,
		status: 'active',
		author: fields.author,
		drawCount: 0,
		lastDrawnAt: null,
		createdAt: now,
		updatedAt: now
	});

	await writeJoins(id, fields);
	return id;
}

/**
 * Updates a card in place. The id is NOT recomputed — it was assigned at
 * creation and editing the text must not change it (§5). draw_count,
 * last_drawn_at, created_at and author history are left alone here.
 */
export async function updateCard(
	id: string,
	fields: CardFields,
	status: CardStatus
): Promise<void> {
	await db
		.update(cards)
		.set({
			text: fields.text,
			favorite: fields.favorite,
			author: fields.author,
			status,
			updatedAt: Date.now()
		})
		.where(eq(cards.id, id));

	await writeJoins(id, fields);
}

/** Archive is the soft delete and the default action (§9). */
export async function setCardStatus(id: string, status: CardStatus): Promise<void> {
	await db.update(cards).set({ status, updatedAt: Date.now() }).where(eq(cards.id, id));
}

export async function setFavorite(id: string, favorite: boolean): Promise<void> {
	await db.update(cards).set({ favorite, updatedAt: Date.now() }).where(eq(cards.id, id));
}

/**
 * Permanent. Cascades through card_kinds, card_gear, card_tags and draw_cards
 * (§9), which is why it asks for confirmation in the UI.
 */
export async function deleteCard(id: string): Promise<void> {
	await db.delete(cards).where(eq(cards.id, id));
}

export async function cardExists(id: string): Promise<boolean> {
	const [row] = await db.select({ id: cards.id }).from(cards).where(eq(cards.id, id)).limit(1);
	return Boolean(row);
}
