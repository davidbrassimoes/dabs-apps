import { sql } from 'drizzle-orm';
import { db } from './index';
import { cards, gear } from './schema';
import { SEED_GEAR, SEED_SOURCE, STARTER_CARDS_YAML } from '../../../../seed/index';
import { applyImport, parseImport } from '../import';

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

/**
 * Puts Appendix B's starter cards into an empty cards table, so the app is
 * usable the moment it is built (§15, M5). Runs after the gear seed, since
 * the starter cards reference gear slugs that must already exist.
 *
 * Only when the table is empty — same reasoning as the gear seed. It goes
 * through the real importer (parseImport / applyImport) rather than writing
 * rows directly, so the starter set is held to exactly the same validation a
 * hand-written import would be.
 */
export async function seedCardsIfEmpty(): Promise<number> {
	const [{ count }] = await db.select({ count: sql<number>`count(*)` }).from(cards);
	if (count > 0) return 0;

	const gearRows = await db.select({ id: gear.id }).from(gear);
	const knownGear = new Set(gearRows.map((r) => r.id));

	const report = parseImport(STARTER_CARDS_YAML, { knownGear, existingCards: new Map() });
	if (report.fileError) {
		throw new Error(`${SEED_SOURCE.cards} failed to parse: ${report.fileError}`);
	}
	if (report.counts.error > 0) {
		const first = report.rows.find((r) => r.class === 'error');
		throw new Error(`${SEED_SOURCE.cards} has a bad card: ${first?.issue?.message}`);
	}

	const summary = await applyImport(report.rows);
	return summary.created;
}
