import { describe, expect, it } from 'vitest';
import {
	RELAXED_WINDOW,
	SUPPRESSION_WINDOW,
	drawSingle,
	drawSpread,
	eligibleFor,
	rerollSlot
} from './draw';
import type { Pool, PoolCard, PoolGear } from './draw';
import type { Kind } from './vocab';

// §14's list for draw.ts. These are the tests that matter: draw.ts is pure, so
// they run against fixture arrays with no database and no network.

function card(
	id: string,
	kinds: Kind[],
	gear: string[] = [],
	extra: Partial<PoolCard> = {}
): PoolCard {
	return { id, text: `card ${id}`, kinds, gear, tags: [], favorite: false, ...extra };
}

function gear(id: string, owned: boolean, inRack: boolean): PoolGear {
	return { id, owned, inRack };
}

function pool(cards: PoolCard[], gearList: PoolGear[] = [], overrides: Partial<Pool> = {}): Pool {
	return {
		syncedAt: 0,
		cards,
		gear: gearList,
		recent: [],
		filters: { inRackOnly: true, favouritesOnly: false, tags: [] },
		...overrides
	};
}

/** A deterministic stand-in for Math.random that walks a fixed list. */
function sequence(values: number[]): () => number {
	let i = 0;
	return () => values[i++ % values.length];
}

describe('gear eligibility', () => {
	const inventory = [
		gear('racked', true, true),
		gear('unracked', true, false),
		gear('wishlist', false, false),
		gear('wishlist-racked', false, true)
	];

	it('excludes a card whose gear is out of the rack when inRackOnly', () => {
		const p = pool([card('a', ['idea'], ['unracked'])], inventory);
		expect(eligibleFor(p, 'idea', p.filters)).toHaveLength(0);
	});

	it('includes that same card when inRackOnly is off', () => {
		const p = pool([card('a', ['idea'], ['unracked'])], inventory, {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		expect(eligibleFor(p, 'idea', p.filters).map((c) => c.id)).toEqual(['a']);
	});

	it('excludes a card referencing unowned gear in both cases', () => {
		const c = [card('a', ['idea'], ['wishlist'])];
		const strict = pool(c, inventory);
		const loose = pool(c, inventory, {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		expect(eligibleFor(strict, 'idea', strict.filters)).toHaveLength(0);
		expect(eligibleFor(loose, 'idea', loose.filters)).toHaveLength(0);
	});

	it('excludes unowned gear even when it is somehow flagged in the rack', () => {
		const p = pool([card('a', ['idea'], ['wishlist-racked'])], inventory);
		expect(eligibleFor(p, 'idea', p.filters)).toHaveLength(0);
	});

	it('always allows a card with no gear — "Mono. All of it." is always available', () => {
		const p = pool([card('mono', ['constraint'])], inventory);
		expect(eligibleFor(p, 'constraint', p.filters).map((c) => c.id)).toEqual(['mono']);
	});

	it('requires every piece of gear on a card, not just one', () => {
		const p = pool([card('a', ['idea'], ['racked', 'unracked'])], inventory);
		expect(eligibleFor(p, 'idea', p.filters)).toHaveLength(0);
	});

	it('treats gear missing from the inventory as unavailable', () => {
		const p = pool([card('a', ['idea'], ['ghost'])], inventory);
		expect(eligibleFor(p, 'idea', p.filters)).toHaveLength(0);
	});
});

describe('the other filters', () => {
	it('honours favouritesOnly', () => {
		const cards = [card('plain', ['idea']), card('fav', ['idea'], [], { favorite: true })];
		const p = pool(cards, [], {
			filters: { inRackOnly: false, favouritesOnly: true, tags: [] }
		});
		expect(eligibleFor(p, 'idea', p.filters).map((c) => c.id)).toEqual(['fav']);
	});

	it('matches a card holding at least one of the filter tags', () => {
		const cards = [
			card('a', ['idea'], [], { tags: ['room', 'stereo'] }),
			card('b', ['idea'], [], { tags: ['lo-fi'] }),
			card('c', ['idea'], [], { tags: [] })
		];
		const p = pool(cards, [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: ['room', 'mono'] }
		});
		expect(eligibleFor(p, 'idea', p.filters).map((c) => c.id)).toEqual(['a']);
	});

	it('only offers cards holding the requested kind', () => {
		const cards = [card('a', ['idea']), card('b', ['source']), card('c', ['idea', 'source'])];
		const p = pool(cards, [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		expect(eligibleFor(p, 'source', p.filters).map((c) => c.id)).toEqual(['b', 'c']);
	});
});

describe('the suppression window', () => {
	const many = Array.from({ length: 45 }, (_, i) => card(`c${i}`, ['idea']));

	it('excludes the last 30 drawn ids and lets the 31st back in', () => {
		// 31 ids in the recent list, newest first: only the first 30 are suppressed.
		const recent = many.slice(0, 31).map((c) => c.id);
		const p = pool(many, [], {
			recent,
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		const ids = eligibleFor(p, 'idea', p.filters).map((c) => c.id);

		expect(SUPPRESSION_WINDOW).toBe(30);
		for (const suppressed of recent.slice(0, SUPPRESSION_WINDOW)) {
			expect(ids).not.toContain(suppressed);
		}
		// c30 is the 31st entry, so it has fallen out of the window.
		expect(ids).toContain('c30');
		expect(ids).toHaveLength(15);
	});

	it('suppresses nothing when the recent list is empty', () => {
		const p = pool(many, [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		expect(eligibleFor(p, 'idea', p.filters)).toHaveLength(45);
	});
});

describe('the relaxation ladder', () => {
	const inventory = [gear('unracked', true, false)];

	it('reports no relaxation when the pool is healthy', () => {
		const p = pool([card('a', ['idea'])], [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		expect(drawSingle(p).relaxation).toBe('none');
	});

	it('step 1: shrinks the window from 30 to 10 before anything else', () => {
		// 12 cards, all 12 recently drawn. Shrinking to 10 frees the oldest two.
		const cards = Array.from({ length: 12 }, (_, i) => card(`c${i}`, ['idea']));
		const p = pool(cards, [], {
			recent: cards.map((c) => c.id),
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		const result = drawSingle(p);
		expect(RELAXED_WINDOW).toBe(10);
		expect(result.relaxation).toBe('window10');
		// Only the two that fell outside the shrunken window are reachable.
		expect(['c10', 'c11']).toContain(result.slots[0].card?.id);
	});

	it('step 2: drops the window entirely when shrinking it is not enough', () => {
		const cards = Array.from({ length: 5 }, (_, i) => card(`c${i}`, ['idea']));
		const p = pool(cards, [], {
			recent: cards.map((c) => c.id),
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		const result = drawSingle(p);
		expect(result.relaxation).toBe('noWindow');
		expect(result.slots[0].card).not.toBeNull();
	});

	it('step 3: drops inRackOnly only after the window steps have failed', () => {
		const p = pool([card('a', ['idea'], ['unracked'])], inventory, {
			filters: { inRackOnly: true, favouritesOnly: false, tags: [] }
		});

		const result = drawSingle(p);
		expect(result.relaxation).toBe('noInRack');
		expect(result.slots[0].card?.id).toBe('a');
	});

	it('prefers the window steps over dropping inRackOnly when both would work', () => {
		// Two cards: one racked but recently drawn, one unracked and not drawn.
		// Widening the window must win, because it comes first in the ladder.
		const p = pool(
			[card('racked-recent', ['idea'], ['racked']), card('unracked-fresh', ['idea'], ['unracked'])],
			[gear('racked', true, true), gear('unracked', true, false)],
			{
				recent: ['racked-recent'],
				filters: { inRackOnly: true, favouritesOnly: false, tags: [] }
			}
		);

		const result = drawSingle(p);
		// A window step — here the full drop, since the card is the most recent
		// one and so is still inside a shrunken window. What matters is that the
		// ladder never reaches noInRack while a window step would do.
		expect(result.relaxation).toBe('noWindow');
		expect(result.slots[0].card?.id).toBe('racked-recent');
	});

	it('step 4: gives up with an empty state naming the filter that emptied the pool', () => {
		const p = pool([card('a', ['idea'], [], { favorite: false })], [], {
			filters: { inRackOnly: false, favouritesOnly: true, tags: [] }
		});

		const result = drawSingle(p);
		expect(result.slots[0].card).toBeNull();
		expect(result.slots[0].problem?.filter).toBe('favourites');
	});

	it('never silently ignores a filter the owner set', () => {
		// Tags are not on the ladder: if tags empty the pool, that is the answer.
		const p = pool([card('a', ['idea'], [], { tags: ['room'] })], [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: ['nothing-matches'] }
		});

		const result = drawSingle(p);
		expect(result.slots[0].card).toBeNull();
		expect(result.slots[0].problem?.filter).toBe('tags');
	});

	it('blames the kind when there is simply no card of it', () => {
		const p = pool([card('a', ['idea'])], [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		const result = drawSpread(p);
		expect(result.slots.every((s) => s.card === null)).toBe(true);
		expect(result.slots[0].problem?.filter).toBe('kind');
	});
});

describe('single draws', () => {
	it('draws one card of kind idea, with a null slot kind', () => {
		const p = pool([card('a', ['idea']), card('b', ['source'])], [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});
		const result = drawSingle(p);
		expect(result.mode).toBe('single');
		expect(result.slots).toHaveLength(1);
		expect(result.slots[0].kind).toBeNull();
		expect(result.slots[0].card?.id).toBe('a');
	});
});

describe('spreads', () => {
	const spreadPool = () =>
		pool(
			[
				card('s1', ['source']),
				card('s2', ['source']),
				card('t1', ['treatment']),
				card('c1', ['constraint'])
			],
			[],
			{ filters: { inRackOnly: false, favouritesOnly: false, tags: [] } }
		);

	it('fills source, treatment and constraint in that order', () => {
		const result = drawSpread(spreadPool());
		expect(result.mode).toBe('spread');
		expect(result.slots.map((s) => s.kind)).toEqual(['source', 'treatment', 'constraint']);
	});

	it('cannot use one card for two slots even when it holds both kinds', () => {
		// `both` is the only card holding source AND treatment. It may take one
		// slot; the other must fall to a different card or stay empty.
		const p = pool([card('both', ['source', 'treatment']), card('c1', ['constraint'])], [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		const result = drawSpread(p);
		const used = result.slots.map((s) => s.card?.id).filter(Boolean);
		expect(new Set(used).size).toBe(used.length);
		expect(used.filter((id) => id === 'both')).toHaveLength(1);
	});

	it('never repeats a card across slots over many draws', () => {
		const p = pool(
			[
				card('a', ['source', 'treatment', 'constraint']),
				card('b', ['source', 'treatment', 'constraint']),
				card('c', ['source', 'treatment', 'constraint'])
			],
			[],
			{ filters: { inRackOnly: false, favouritesOnly: false, tags: [] } }
		);

		for (let i = 0; i < 500; i++) {
			const ids = drawSpread(p)
				.slots.map((s) => s.card?.id)
				.filter(Boolean);
			expect(new Set(ids).size).toBe(ids.length);
		}
	});

	it('keeps a locked slot and redraws the others', () => {
		const p = spreadPool();
		const first = drawSpread(p);
		const lockedCard = first.slots[0].card!;

		const second = drawSpread(p, { locked: [lockedCard.id, null, null] });
		expect(second.slots[0].card?.id).toBe(lockedCard.id);
	});

	it('will not hand a locked card to another slot', () => {
		const p = pool(
			[
				card('both', ['source', 'treatment']),
				card('t2', ['treatment']),
				card('c1', ['constraint'])
			],
			[],
			{ filters: { inRackOnly: false, favouritesOnly: false, tags: [] } }
		);

		const result = drawSpread(p, { locked: ['both', null, null] });
		expect(result.slots[0].card?.id).toBe('both');
		expect(result.slots[1].card?.id).toBe('t2');
	});
});

describe('reroll', () => {
	it('replaces one slot and leaves the others alone', () => {
		const p = pool(
			[
				card('s1', ['source']),
				card('s2', ['source']),
				card('t1', ['treatment']),
				card('c1', ['constraint'])
			],
			[],
			{ filters: { inRackOnly: false, favouritesOnly: false, tags: [] } }
		);

		const first = drawSpread(p);
		const rerolled = rerollSlot(p, first, 1);

		expect(rerolled.slots[0].card?.id).toBe(first.slots[0].card?.id);
		expect(rerolled.slots[2].card?.id).toBe(first.slots[2].card?.id);
		expect(rerolled.slots[1].kind).toBe('treatment');
	});

	it('does not reroll into a card another slot is already holding', () => {
		const p = pool(
			[
				card('both', ['source', 'treatment']),
				card('t1', ['treatment']),
				card('c1', ['constraint'])
			],
			[],
			{ filters: { inRackOnly: false, favouritesOnly: false, tags: [] } }
		);

		const first = drawSpread(p, { locked: ['both', null, null] });
		for (let i = 0; i < 50; i++) {
			const rerolled = rerollSlot(p, first, 1);
			expect(rerolled.slots[1].card?.id).not.toBe('both');
		}
	});
});

describe('selection is uniform', () => {
	it('gives each of five cards about a fifth of 10 000 draws', () => {
		const cards = Array.from({ length: 5 }, (_, i) => card(`c${i}`, ['idea']));
		const p = pool(cards, [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		const counts = new Map<string, number>();
		for (let i = 0; i < 10_000; i++) {
			const id = drawSingle(p).slots[0].card!.id;
			counts.set(id, (counts.get(id) ?? 0) + 1);
		}

		expect(counts.size).toBe(5);
		for (const c of cards) {
			expect(counts.get(c.id) ?? 0).toBeGreaterThan(1800);
			expect(counts.get(c.id) ?? 0).toBeLessThan(2200);
		}
	});

	it('uses the injected random source rather than Math.random', () => {
		const cards = Array.from({ length: 4 }, (_, i) => card(`c${i}`, ['idea']));
		const p = pool(cards, [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		// 0 picks the first eligible card, 0.999… the last.
		expect(drawSingle(p, { random: sequence([0]) }).slots[0].card?.id).toBe('c0');
		expect(drawSingle(p, { random: sequence([0.9999]) }).slots[0].card?.id).toBe('c3');
	});

	it('is weighted by nothing — favourites do not change the odds', () => {
		const cards = [card('plain', ['idea']), card('fav', ['idea'], [], { favorite: true })];
		const p = pool(cards, [], {
			filters: { inRackOnly: false, favouritesOnly: false, tags: [] }
		});

		let favCount = 0;
		for (let i = 0; i < 4000; i++) {
			if (drawSingle(p).slots[0].card?.id === 'fav') favCount++;
		}
		expect(favCount).toBeGreaterThan(1850);
		expect(favCount).toBeLessThan(2150);
	});
});

describe('purity', () => {
	it('does not mutate the pool it is given', () => {
		const p = pool(
			[
				card('a', ['idea']),
				card('s', ['source']),
				card('t', ['treatment']),
				card('c', ['constraint'])
			],
			[],
			{ recent: ['x'], filters: { inRackOnly: false, favouritesOnly: false, tags: [] } }
		);
		const snapshot = JSON.stringify(p);

		drawSingle(p);
		drawSpread(p);
		rerollSlot(p, drawSpread(p), 0);

		expect(JSON.stringify(p)).toBe(snapshot);
	});
});
