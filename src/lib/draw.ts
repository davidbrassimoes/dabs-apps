import { SINGLE_KIND, SPREAD_SLOTS } from './kinds';
import type { Kind } from './vocab';

// The randomiser (§6).
//
// PURE. It takes candidate cards, the filters and the recent-draw list, and
// returns the chosen cards. No database, no fetch, no imports from server/. It
// runs in the browser, from a locally cached pool, so drawing works with the
// network off and there is one code path online and off.
//
// There is no endpoint that performs a draw. If one appears, the architecture
// has drifted — re-read §11.
//
// Phase 2 adds a `weight` column to cards and changes only `pick()` below.
// Nothing else in this file needs to move.

export type PoolCard = {
	id: string;
	text: string;
	kinds: Kind[];
	gear: string[];
	tags: string[];
	favorite: boolean;
};

export type PoolGear = { id: string; owned: boolean; inRack: boolean };

export type DrawFilters = {
	inRackOnly: boolean;
	favouritesOnly: boolean;
	tags: string[];
};

export type Pool = {
	syncedAt: number;
	cards: PoolCard[];
	gear: PoolGear[];
	/** Last 30 drawn card ids, newest first. Includes queued-but-unsent draws. */
	recent: string[];
	filters: DrawFilters;
};

/** The last 30 drawn cards are excluded (§2). */
export const SUPPRESSION_WINDOW = 30;

/** Step 1 of the ladder shrinks the window to this. */
export const RELAXED_WINDOW = 10;

/**
 * How far down §6's ladder we had to go to fill a slot. Shown to the owner:
 * never silently ignore a filter he set.
 */
export type Relaxation = 'none' | 'window10' | 'noWindow' | 'noInRack';

/**
 * The order §6's ladder steps through. Exported so the UI can tell "this slot
 * succeeded after relaxing a step" apart from "this slot failed outright" —
 * both end up carrying the 'noInRack' tag internally, but only the first is a
 * relaxation worth announcing.
 */
export const RELAXATION_LADDER: Relaxation[] = ['none', 'window10', 'noWindow', 'noInRack'];
const LADDER = RELAXATION_LADDER;

export type EmptyFilter = 'favourites' | 'tags' | 'inRack' | 'kind';

export type EmptyReason = { filter: EmptyFilter; message: string };

export type DrawSlot = {
	/** null for a single draw; the slot's kind for a spread. */
	kind: Kind | null;
	card: PoolCard | null;
	relaxation: Relaxation;
	problem: EmptyReason | null;
};

export type DrawResult = {
	mode: 'single' | 'spread';
	slots: DrawSlot[];
	/** The deepest step any slot needed. */
	relaxation: Relaxation;
};

type Options = {
	random?: () => number;
	/** Card ids already committed to a slot, or locked by the owner. */
	exclude?: string[];
};

type Level = { window: number | null; inRackOnly: boolean };

function levelFor(relaxation: Relaxation, filters: DrawFilters): Level {
	switch (relaxation) {
		case 'none':
			return { window: SUPPRESSION_WINDOW, inRackOnly: filters.inRackOnly };
		case 'window10':
			return { window: RELAXED_WINDOW, inRackOnly: filters.inRackOnly };
		case 'noWindow':
			return { window: null, inRackOnly: filters.inRackOnly };
		case 'noInRack':
			return { window: null, inRackOnly: false };
	}
}

/**
 * Rule 3 and 4 of §6: every piece of gear a card names must be owned, and — if
 * inRackOnly — also in the rack. Gear missing from the inventory counts as
 * unavailable rather than as no constraint.
 *
 * A card with no gear passes trivially. That is intended: "Mono. All of it."
 * should always be available.
 */
function gearAllows(card: PoolCard, byId: Map<string, PoolGear>, inRackOnly: boolean): boolean {
	return card.gear.every((slug) => {
		const item = byId.get(slug);
		if (!item || !item.owned) return false;
		return inRackOnly ? item.inRack : true;
	});
}

function matches(
	card: PoolCard,
	kind: Kind,
	filters: DrawFilters,
	byId: Map<string, PoolGear>,
	level: Level,
	suppressed: Set<string>,
	exclude: Set<string>
): boolean {
	if (!card.kinds.includes(kind)) return false;
	if (!gearAllows(card, byId, level.inRackOnly)) return false;
	if (filters.favouritesOnly && !card.favorite) return false;
	if (filters.tags.length > 0 && !card.tags.some((t) => filters.tags.includes(t))) return false;
	if (suppressed.has(card.id)) return false;
	if (exclude.has(card.id)) return false;
	return true;
}

function gearIndex(pool: Pool): Map<string, PoolGear> {
	return new Map(pool.gear.map((g) => [g.id, g]));
}

function suppressedSet(recent: string[], window: number | null): Set<string> {
	return new Set(window === null ? [] : recent.slice(0, window));
}

/**
 * The eligible set for one slot, at full strictness. Exported because it is the
 * heart of §6 and the tests drive it directly.
 */
export function eligibleFor(
	pool: Pool,
	kind: Kind,
	filters: DrawFilters,
	options: { relaxation?: Relaxation; exclude?: string[] } = {}
): PoolCard[] {
	const level = levelFor(options.relaxation ?? 'none', filters);
	const byId = gearIndex(pool);
	const suppressed = suppressedSet(pool.recent, level.window);
	const exclude = new Set(options.exclude ?? []);

	return pool.cards.filter((card) =>
		matches(card, kind, filters, byId, level, suppressed, exclude)
	);
}

/** Uniform random over the eligible set. Nothing else (§6). */
function pick(cards: PoolCard[], random: () => number): PoolCard {
	return cards[Math.floor(random() * cards.length)];
}

/**
 * Works down §6's ladder in order and stops at the first step that yields
 * anything: shrink the window to 10, drop the window, drop inRackOnly.
 */
function fillSlot(
	pool: Pool,
	kind: Kind,
	slotKind: Kind | null,
	options: Required<Options>
): DrawSlot {
	for (const relaxation of LADDER) {
		const candidates = eligibleFor(pool, kind, pool.filters, {
			relaxation,
			exclude: options.exclude
		});
		if (candidates.length > 0) {
			return { kind: slotKind, card: pick(candidates, options.random), relaxation, problem: null };
		}
	}

	return {
		kind: slotKind,
		card: null,
		relaxation: 'noInRack',
		problem: explainEmpty(pool, kind, options.exclude)
	};
}

/**
 * Step 4: name the filter that emptied the pool, so the empty state can link to
 * it. Tags and favourites are never relaxed automatically — they are choices
 * the owner made, and §6 says never to ignore one silently.
 */
function explainEmpty(pool: Pool, kind: Kind, exclude: string[]): EmptyReason {
	const { filters } = pool;
	const byId = gearIndex(pool);
	const excluded = new Set(exclude);
	const ofKind = pool.cards.filter((c) => c.kinds.includes(kind) && !excluded.has(c.id));

	if (ofKind.length === 0) {
		return { filter: 'kind', message: `No ${kind} cards to draw from.` };
	}

	if (filters.favouritesOnly && ofKind.every((c) => !c.favorite)) {
		return {
			filter: 'favourites',
			message: `No favourite ${kind} cards. Turn favourites off, or mark some.`
		};
	}

	if (
		filters.tags.length > 0 &&
		ofKind.every((c) => !c.tags.some((t) => filters.tags.includes(t)))
	) {
		return {
			filter: 'tags',
			message: `No ${kind} cards tagged ${filters.tags.join(' or ')}.`
		};
	}

	if (ofKind.every((c) => !gearAllows(c, byId, false))) {
		return {
			filter: 'inRack',
			message: `Every ${kind} card needs gear you do not own yet.`
		};
	}

	return {
		filter: 'kind',
		message: `Nothing left to draw for ${kind} with these filters.`
	};
}

function deepest(slots: DrawSlot[]): Relaxation {
	return slots.reduce<Relaxation>(
		(worst, slot) =>
			LADDER.indexOf(slot.relaxation) > LADDER.indexOf(worst) ? slot.relaxation : worst,
		'none'
	);
}

function withDefaults(options: Options): Required<Options> {
	return { random: options.random ?? Math.random, exclude: options.exclude ?? [] };
}

/** One complete idea. The slot kind is null (§6). */
export function drawSingle(pool: Pool, options: Options = {}): DrawResult {
	const slot = fillSlot(pool, SINGLE_KIND, null, withDefaults(options));
	return { mode: 'single', slots: [slot], relaxation: slot.relaxation };
}

/**
 * Three slots in order — source, treatment, constraint — each from its own kind
 * pool. A card already placed cannot fill another slot, so a card holding two
 * of the three kinds can only take one of them.
 */
export function drawSpread(
	pool: Pool,
	options: Options & { locked?: (string | null)[] } = {}
): DrawResult {
	const base = withDefaults(options);
	const locked = options.locked ?? [];

	// Locked cards are spoken for before anything is drawn, so a later slot
	// cannot take one.
	const used = new Set<string>([
		...base.exclude,
		...locked.filter((id): id is string => Boolean(id))
	]);

	const slots: DrawSlot[] = SPREAD_SLOTS.map((slotKind, index) => {
		const lockedId = locked[index] ?? null;
		if (lockedId) {
			const card = pool.cards.find((c) => c.id === lockedId) ?? null;
			return { kind: slotKind, card, relaxation: 'none', problem: null };
		}

		const slot = fillSlot(pool, slotKind, slotKind, { ...base, exclude: [...used] });
		if (slot.card) used.add(slot.card.id);
		return slot;
	});

	return { mode: 'spread', slots, relaxation: deepest(slots) };
}

/**
 * Redraws one slot, leaving the others as they are. Rule 8 still holds: the new
 * card cannot be one another slot is already showing.
 */
export function rerollSlot(
	pool: Pool,
	current: DrawResult,
	index: number,
	options: Options = {}
): DrawResult {
	const base = withDefaults(options);
	const target = current.slots[index];
	if (!target) return current;

	const held = current.slots
		.filter((_, i) => i !== index)
		.map((s) => s.card?.id)
		.filter((id): id is string => Boolean(id));

	const kind = target.kind ?? SINGLE_KIND;
	const slot = fillSlot(pool, kind, target.kind, {
		...base,
		exclude: [...base.exclude, ...held]
	});

	const slots = current.slots.map((s, i) => (i === index ? slot : s));
	return { mode: current.mode, slots, relaxation: deepest(slots) };
}
