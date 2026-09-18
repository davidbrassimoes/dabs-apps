import { relations, sql } from 'drizzle-orm';
import { check, index, integer, primaryKey, sqliteTable, text } from 'drizzle-orm/sqlite-core';

// The data model in §4 of the spec. Epoch milliseconds for every timestamp;
// SQLite has no boolean, so flags are integers with mode: 'boolean'.

export const KINDS = [
	'idea',
	'source',
	'treatment',
	'technique',
	'constraint',
	'arrangement'
] as const;
export type Kind = (typeof KINDS)[number];

export const GEAR_CATEGORIES = ['strings', 'keys', 'amps', 'pedals', 'mics', 'small'] as const;
export type GearCategory = (typeof GEAR_CATEGORIES)[number];

export const AUTHORS = ['david', 'claude'] as const;
export type Author = (typeof AUTHORS)[number];

export const CARD_STATUSES = ['active', 'archived'] as const;
export type CardStatus = (typeof CARD_STATUSES)[number];

export const DRAW_MODES = ['single', 'spread'] as const;
export type DrawMode = (typeof DRAW_MODES)[number];

export const cards = sqliteTable(
	'cards',
	{
		// Content-derived, assigned once at creation, never recomputed on update (§5).
		id: text('id').primaryKey(),
		text: text('text').notNull(),
		// A marker only. It has no effect on draw odds (§2).
		favorite: integer('favorite', { mode: 'boolean' }).notNull().default(false),
		status: text('status', { enum: CARD_STATUSES }).notNull().default('active'),
		author: text('author', { enum: AUTHORS }).notNull(),
		// Phase 2 adds `weight INTEGER NOT NULL DEFAULT 1` here and changes only the
		// selection step in draw.ts. Nothing else in the draw needs to move.
		drawCount: integer('draw_count').notNull().default(0),
		lastDrawnAt: integer('last_drawn_at'),
		createdAt: integer('created_at').notNull(),
		updatedAt: integer('updated_at').notNull()
	},
	(t) => [
		index('idx_cards_status').on(t.status),
		check('cards_status_check', sql`${t.status} IN ('active','archived')`),
		check('cards_author_check', sql`${t.author} IN ('david','claude')`)
	]
);

const kindList = sql.raw(KINDS.map((k) => `'${k}'`).join(','));

export const cardKinds = sqliteTable(
	'card_kinds',
	{
		cardId: text('card_id')
			.notNull()
			.references(() => cards.id, { onDelete: 'cascade' }),
		kind: text('kind', { enum: KINDS }).notNull()
	},
	(t) => [
		primaryKey({ columns: [t.cardId, t.kind] }),
		index('idx_card_kinds_kind').on(t.kind),
		check('card_kinds_kind_check', sql`${t.kind} IN (${kindList})`)
	]
);

export const cardGear = sqliteTable(
	'card_gear',
	{
		cardId: text('card_id')
			.notNull()
			.references(() => cards.id, { onDelete: 'cascade' }),
		// RESTRICT: gear that cards still reference cannot be deleted (§4).
		gearId: text('gear_id')
			.notNull()
			.references(() => gear.id, { onDelete: 'restrict' })
	},
	(t) => [primaryKey({ columns: [t.cardId, t.gearId] }), index('idx_card_gear_gear').on(t.gearId)]
);

export const cardTags = sqliteTable(
	'card_tags',
	{
		cardId: text('card_id')
			.notNull()
			.references(() => cards.id, { onDelete: 'cascade' }),
		tag: text('tag').notNull()
	},
	(t) => [primaryKey({ columns: [t.cardId, t.tag] })]
);

export const gear = sqliteTable(
	'gear',
	{
		// Slug. Matches ^[a-z0-9][a-z0-9-]*$ and is the vocabulary card imports use.
		id: text('id').primaryKey(),
		name: text('name').notNull(),
		category: text('category', { enum: GEAR_CATEGORIES }).notNull(),
		// owned: false is the wishlist. Cards may reference it; they never draw (§7).
		owned: integer('owned', { mode: 'boolean' }).notNull().default(true),
		// "Patched and reachable tonight". Global, not per-session.
		inRack: integer('in_rack', { mode: 'boolean' }).notNull().default(false),
		sort: integer('sort').notNull().default(0)
	},
	(t) => [
		check(
			'gear_category_check',
			sql`${t.category} IN (${sql.raw(GEAR_CATEGORIES.map((c) => `'${c}'`).join(','))})`
		)
	]
);

export const draws = sqliteTable(
	'draws',
	{
		id: integer('id').primaryKey({ autoIncrement: true }),
		// Minted by the client at draw time so replays of a queued record are
		// idempotent (§6). POST /api/draws upserts on this.
		uuid: text('uuid').notNull().unique(),
		drawnAt: integer('drawn_at').notNull(),
		mode: text('mode', { enum: DRAW_MODES }).notNull()
	},
	(t) => [
		index('idx_draws_drawn_at').on(sql`${t.drawnAt} DESC`),
		check('draws_mode_check', sql`${t.mode} IN ('single','spread')`)
	]
);

export const drawCards = sqliteTable(
	'draw_cards',
	{
		drawId: integer('draw_id')
			.notNull()
			.references(() => draws.id, { onDelete: 'cascade' }),
		// 0 for single; 0, 1, 2 for spread.
		slot: integer('slot').notNull(),
		// null for single.
		slotKind: text('slot_kind', { enum: KINDS }),
		cardId: text('card_id')
			.notNull()
			.references(() => cards.id, { onDelete: 'cascade' })
	},
	(t) => [
		primaryKey({ columns: [t.drawId, t.slot] }),
		index('idx_draw_cards_card').on(t.cardId),
		check(
			'draw_cards_slot_kind_check',
			sql`${t.slotKind} IS NULL OR ${t.slotKind} IN (${kindList})`
		)
	]
);

// Exactly three keys in phase 1: theme, drawFilters, schemaVersion. Values are JSON.
export const settings = sqliteTable('settings', {
	key: text('key').primaryKey(),
	value: text('value').notNull()
});

export const cardsRelations = relations(cards, ({ many }) => ({
	kinds: many(cardKinds),
	gear: many(cardGear),
	tags: many(cardTags)
}));

export const cardKindsRelations = relations(cardKinds, ({ one }) => ({
	card: one(cards, { fields: [cardKinds.cardId], references: [cards.id] })
}));

export const cardGearRelations = relations(cardGear, ({ one }) => ({
	card: one(cards, { fields: [cardGear.cardId], references: [cards.id] }),
	gear: one(gear, { fields: [cardGear.gearId], references: [gear.id] })
}));

export const cardTagsRelations = relations(cardTags, ({ one }) => ({
	card: one(cards, { fields: [cardTags.cardId], references: [cards.id] })
}));

export const drawsRelations = relations(draws, ({ many }) => ({
	cards: many(drawCards)
}));

export const drawCardsRelations = relations(drawCards, ({ one }) => ({
	draw: one(draws, { fields: [drawCards.drawId], references: [draws.id] }),
	card: one(cards, { fields: [drawCards.cardId], references: [cards.id] })
}));
