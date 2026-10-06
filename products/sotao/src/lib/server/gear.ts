import { asc, eq, sql } from 'drizzle-orm';
import { db } from './db';
import { cardGear, gear } from './db/schema';
import type { GearCategory } from './db/schema';

export type GearRow = {
	id: string;
	name: string;
	category: GearCategory;
	owned: boolean;
	inRack: boolean;
	sort: number;
};

export type RackCount = {
	/** How many owned pieces are patched and reachable tonight. */
	inRack: number;
	/** How many pieces are owned. The header reads "In the rack · N of 42". */
	owned: number;
};

export async function listGear(): Promise<GearRow[]> {
	return db.select().from(gear).orderBy(asc(gear.category), asc(gear.sort), asc(gear.name));
}

/**
 * The numbers behind the header chip. Only owned gear counts: wishlist items
 * cannot be racked, so counting them would make the denominator a lie.
 */
export async function rackCount(): Promise<RackCount> {
	const [row] = await db
		.select({
			inRack: sql<number>`sum(case when ${gear.owned} = 1 and ${gear.inRack} = 1 then 1 else 0 end)`,
			owned: sql<number>`sum(case when ${gear.owned} = 1 then 1 else 0 end)`
		})
		.from(gear);
	return { inRack: Number(row?.inRack ?? 0), owned: Number(row?.owned ?? 0) };
}

export async function setInRack(id: string, inRack: boolean): Promise<void> {
	// Wishlist gear can never be racked, whatever the form says.
	await db
		.update(gear)
		.set({ inRack })
		.where(sql`${gear.id} = ${id} and ${gear.owned} = 1`);
}

/** "Everything" and "Clear" — the bulk actions on the toolbar. */
export async function setAllInRack(inRack: boolean): Promise<void> {
	await db.update(gear).set({ inRack }).where(eq(gear.owned, true));
}

/**
 * Flipping a wishlist item to owned is how cards written ahead of a purchase
 * come alive. Going the other way un-racks it, since it cannot be patched.
 */
export async function setOwned(id: string, owned: boolean): Promise<void> {
	await db
		.update(gear)
		.set(owned ? { owned } : { owned, inRack: false })
		.where(eq(gear.id, id));
}

export async function gearExists(id: string): Promise<boolean> {
	const [row] = await db.select({ id: gear.id }).from(gear).where(eq(gear.id, id)).limit(1);
	return Boolean(row);
}

export async function addGear(row: {
	id: string;
	name: string;
	category: GearCategory;
	owned: boolean;
}): Promise<void> {
	// New gear sorts after everything already in its category.
	const [last] = await db
		.select({ max: sql<number | null>`max(${gear.sort})` })
		.from(gear)
		.where(eq(gear.category, row.category));

	await db.insert(gear).values({
		...row,
		inRack: false,
		sort: (last?.max ?? -1) + 1
	});
}

/**
 * Which cards reference a piece of gear. card_gear is ON DELETE RESTRICT, so
 * this is what the screen shows instead of a failed delete (§4).
 */
export async function cardsUsingGear(id: string): Promise<number> {
	const [row] = await db
		.select({ count: sql<number>`count(*)` })
		.from(cardGear)
		.where(eq(cardGear.gearId, id));
	return Number(row?.count ?? 0);
}
