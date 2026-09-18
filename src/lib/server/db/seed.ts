import { sql } from 'drizzle-orm';
import { db } from './index';
import { gear } from './schema';
import { SEED_GEAR } from '../../../../seed/gear';

/**
 * Puts the §7 inventory into an empty gear table.
 *
 * Only when it is empty. Re-seeding on every boot would resurrect gear the
 * owner had removed, and overwrite the in_rack state he actually cares about.
 * `sort` is the position within the category, so the screen lists each group in
 * the order the spec writes it.
 */
export async function seedGearIfEmpty(): Promise<number> {
	const [{ count }] = await db.select({ count: sql<number>`count(*)` }).from(gear);
	if (count > 0) return 0;

	const rows = SEED_GEAR.map((item, index) => ({
		id: item.id,
		name: item.name,
		category: item.category,
		owned: item.owned ?? true,
		inRack: false,
		sort: index
	}));

	await db.insert(gear).values(rows);
	return rows.length;
}
