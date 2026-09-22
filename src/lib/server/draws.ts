import { and, desc, eq, gte, inArray, sql } from 'drizzle-orm';
import { db } from './db';
import { cardGear, cardKinds, cardTags, cards, drawCards, draws, gear } from './db/schema';
import { getDrawFilters } from './settings';
import type { Pool, PoolCard } from '$lib/draw';
import type { DrawRecord } from '$lib/outbox';
import { SUPPRESSION_WINDOW } from '$lib/draw';
import type { Kind } from '$lib/vocab';

/** Everything the client needs to draw (§11). Active cards only. */
export async function buildPool(): Promise<Pool> {
	const [cardRows, kindRows, gearRefRows, tagRows, gearRows, filters] = await Promise.all([
		db
			.select({ id: cards.id, text: cards.text, favorite: cards.favorite })
			.from(cards)
			.where(eq(cards.status, 'active')),
		db
			.select({ cardId: cardKinds.cardId, kind: cardKinds.kind })
			.from(cardKinds)
			.innerJoin(cards, eq(cards.id, cardKinds.cardId))
			.where(eq(cards.status, 'active')),
		db
			.select({ cardId: cardGear.cardId, gearId: cardGear.gearId })
			.from(cardGear)
			.innerJoin(cards, eq(cards.id, cardGear.cardId))
			.where(eq(cards.status, 'active')),
		db
			.select({ cardId: cardTags.cardId, tag: cardTags.tag })
			.from(cardTags)
			.innerJoin(cards, eq(cards.id, cardTags.cardId))
			.where(eq(cards.status, 'active')),
		db.select({ id: gear.id, owned: gear.owned, inRack: gear.inRack }).from(gear),
		getDrawFilters()
	]);

	const kindsBy = new Map<string, Kind[]>();
	const gearBy = new Map<string, string[]>();
	const tagsBy = new Map<string, string[]>();
	const push = <T>(map: Map<string, T[]>, key: string, value: T) => {
		const list = map.get(key);
		if (list) list.push(value);
		else map.set(key, [value]);
	};

	for (const row of kindRows) push(kindsBy, row.cardId, row.kind);
	for (const row of gearRefRows) push(gearBy, row.cardId, row.gearId);
	for (const row of tagRows) push(tagsBy, row.cardId, row.tag);

	const poolCards: PoolCard[] = cardRows.map((row) => ({
		id: row.id,
		text: row.text,
		kinds: kindsBy.get(row.id) ?? [],
		gear: gearBy.get(row.id) ?? [],
		tags: tagsBy.get(row.id) ?? [],
		favorite: row.favorite
	}));

	return {
		syncedAt: Date.now(),
		cards: poolCards,
		gear: gearRows,
		recent: await recentCardIds(),
		filters
	};
}

/** The last 30 drawn card ids, newest first (§11). */
export async function recentCardIds(limit = SUPPRESSION_WINDOW): Promise<string[]> {
	const rows = await db
		.select({ cardId: drawCards.cardId })
		.from(drawCards)
		.innerJoin(draws, eq(draws.id, drawCards.drawId))
		.orderBy(desc(draws.drawnAt), desc(draws.id), drawCards.slot)
		.limit(limit);
	return rows.map((r) => r.cardId);
}

export type RecordOutcome = 'created' | 'updated' | 'unchanged';

/**
 * Records a completed draw. Upserts on the client-minted uuid, so replaying a
 * queued record must not double-count draw_count (§6).
 *
 * A reroll posts the same uuid with a changed slot, which is an update: the
 * newly drawn card gains a count, and the card it replaced is left with the one
 * it already had. Undoing that would need a history this phase does not keep.
 */
export async function recordDraw(record: DrawRecord): Promise<RecordOutcome> {
	const known = new Set(
		(
			await db
				.select({ id: cards.id })
				.from(cards)
				.where(
					inArray(
						cards.id,
						record.cards.map((c) => c.cardId)
					)
				)
		).map((r) => r.id)
	);

	const missing = record.cards.filter((c) => !known.has(c.cardId));
	if (missing.length > 0) {
		// A card was deleted on another device. The client drops the record
		// rather than retrying forever (§11), so this must be a 4xx.
		throw new UnknownCardError(missing.map((c) => c.cardId));
	}

	const [existing] = await db
		.select({ id: draws.id })
		.from(draws)
		.where(eq(draws.uuid, record.uuid))
		.limit(1);

	if (!existing) {
		const [created] = await db
			.insert(draws)
			.values({ uuid: record.uuid, drawnAt: record.drawnAt, mode: record.mode })
			.returning({ id: draws.id });

		await db.insert(drawCards).values(
			record.cards.map((c) => ({
				drawId: created.id,
				slot: c.slot,
				slotKind: c.slotKind,
				cardId: c.cardId
			}))
		);

		await bumpCounts(
			record.cards.map((c) => c.cardId),
			record.drawnAt
		);
		return 'created';
	}

	// Already seen. Only genuinely new slot occupants count.
	const previous = await db
		.select({ slot: drawCards.slot, cardId: drawCards.cardId })
		.from(drawCards)
		.where(eq(drawCards.drawId, existing.id));

	const before = new Map(previous.map((p) => [p.slot, p.cardId]));
	const changed = record.cards.filter((c) => before.get(c.slot) !== c.cardId);

	if (changed.length === 0) return 'unchanged';

	for (const slot of changed) {
		await db
			.insert(drawCards)
			.values({
				drawId: existing.id,
				slot: slot.slot,
				slotKind: slot.slotKind,
				cardId: slot.cardId
			})
			.onConflictDoUpdate({
				target: [drawCards.drawId, drawCards.slot],
				set: { cardId: slot.cardId, slotKind: slot.slotKind }
			});
	}

	await bumpCounts(
		changed.map((c) => c.cardId),
		record.drawnAt
	);
	return 'updated';
}

async function bumpCounts(cardIds: string[], drawnAt: number): Promise<void> {
	if (cardIds.length === 0) return;
	await db
		.update(cards)
		.set({
			drawCount: sql`${cards.drawCount} + 1`,
			lastDrawnAt: sql`max(coalesce(${cards.lastDrawnAt}, 0), ${drawnAt})`
		})
		.where(inArray(cards.id, cardIds));
}

export class UnknownCardError extends Error {
	constructor(public readonly ids: string[]) {
		super(`Unknown card ids: ${ids.join(', ')}`);
		this.name = 'UnknownCardError';
	}
}

export type TonightDraw = {
	uuid: string;
	drawnAt: number;
	mode: 'single' | 'spread';
	cards: { slot: number; slotKind: Kind | null; cardId: string }[];
};

/** Tonight is the last 8 hours (§6). A plain read; stepping back records nothing. */
export async function tonightsDraws(now = Date.now()): Promise<TonightDraw[]> {
	const since = now - 8 * 60 * 60 * 1000;

	const rows = await db
		.select({
			uuid: draws.uuid,
			drawnAt: draws.drawnAt,
			mode: draws.mode,
			slot: drawCards.slot,
			slotKind: drawCards.slotKind,
			cardId: drawCards.cardId
		})
		.from(draws)
		.innerJoin(drawCards, eq(drawCards.drawId, draws.id))
		.where(and(gte(draws.drawnAt, since)))
		.orderBy(draws.drawnAt, draws.id, drawCards.slot);

	const byUuid = new Map<string, TonightDraw>();
	for (const row of rows) {
		let entry = byUuid.get(row.uuid);
		if (!entry) {
			entry = { uuid: row.uuid, drawnAt: row.drawnAt, mode: row.mode, cards: [] };
			byUuid.set(row.uuid, entry);
		}
		entry.cards.push({ slot: row.slot, slotKind: row.slotKind, cardId: row.cardId });
	}

	return [...byUuid.values()];
}
