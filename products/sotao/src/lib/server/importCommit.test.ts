import { eq } from 'drizzle-orm';
import { beforeAll, describe, expect, it } from 'vitest';
import { db, migrateDb } from './db';
import { cardGear, cardKinds, cards, gear } from './db/schema';
import { applyImport, type ImportRow } from './import';

// The commit is one db.batch, atomic on both drivers (D1 has no transactions).
// Run against libSQL in memory, which migrates and seeds exactly as the Worker
// does against D1.

function row(text: string, id: string, gear: string[]): ImportRow {
	return {
		index: 1,
		line: 1,
		class: 'new',
		id,
		text,
		kinds: ['idea'],
		gear,
		tags: [],
		favorite: false,
		author: 'david',
		status: 'active',
		changed: [],
		issue: null
	};
}

describe('applyImport', () => {
	beforeAll(async () => {
		await migrateDb();
	});

	it('writes new cards with their kinds and gear', async () => {
		const [anyGear] = await db.select().from(gear).limit(1);
		const summary = await applyImport([row('Commit test card A', 'commit-test-a', [anyGear.id])]);

		expect(summary).toEqual({ created: 1, updated: 0 });
		expect(
			await db.select().from(cardKinds).where(eq(cardKinds.cardId, 'commit-test-a'))
		).toHaveLength(1);
		expect(
			await db.select().from(cardGear).where(eq(cardGear.cardId, 'commit-test-a'))
		).toHaveLength(1);
	});

	it('leaves nothing written when one statement fails', async () => {
		const [anyGear] = await db.select().from(gear).limit(1);

		await expect(
			applyImport([
				row('Commit test card B', 'commit-test-b', [anyGear.id]),
				// Unknown gear: the foreign key refuses it, after card B was queued.
				row('Commit test card C', 'commit-test-c', ['no-such-gear'])
			])
		).rejects.toThrow();

		expect(await db.select().from(cards).where(eq(cards.id, 'commit-test-b'))).toHaveLength(0);
		expect(await db.select().from(cards).where(eq(cards.id, 'commit-test-c'))).toHaveLength(0);
	});
});
