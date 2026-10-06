import { stringify } from 'yaml';
import { asc } from 'drizzle-orm';
import { db } from './db';
import { cardGear, cardKinds, cardTags, cards } from './db/schema';

// db → YAML (§8). Every card, including archived ones, in exactly the import
// format, so a round-trip is lossless. This is the backup story.

export type ExportCard = {
	text: string;
	kinds: string[];
	gear: string[];
	tags: string[];
	favorite: boolean;
	author: string;
	/**
	 * Not part of §8's schema table, but the export section explicitly asks
	 * for it so an archived card round-trips rather than reviving as active on
	 * re-import. parseImport accepts it for exactly that reason; see the note
	 * on ImportRow.status.
	 */
	status?: string;
};

/**
 * Pure: the shape and the YAML stringification, with no database in it. Split
 * out from exportYaml() so the round-trip property — export, then import,
 * changes nothing — is testable with fixtures rather than a live database.
 */
export function buildExportYaml(exportCards: ExportCard[]): string {
	const doc = { version: 1, cards: exportCards };
	return `# sótão — export v1\n${stringify(doc, { lineWidth: 0 })}`;
}

export async function exportYaml(): Promise<string> {
	const [cardRows, kindRows, gearRows, tagRows] = await Promise.all([
		db.select().from(cards).orderBy(asc(cards.createdAt)),
		db.select().from(cardKinds),
		db.select().from(cardGear),
		db.select().from(cardTags)
	]);

	const push = <T>(map: Map<string, T[]>, key: string, value: T) => {
		const list = map.get(key);
		if (list) list.push(value);
		else map.set(key, [value]);
	};

	const kindsBy = new Map<string, string[]>();
	const gearBy = new Map<string, string[]>();
	const tagsBy = new Map<string, string[]>();
	for (const row of kindRows) push(kindsBy, row.cardId, row.kind);
	for (const row of gearRows) push(gearBy, row.cardId, row.gearId);
	for (const row of tagRows) push(tagsBy, row.cardId, row.tag);

	const exportCards: ExportCard[] = cardRows.map((row) => {
		const card: ExportCard = {
			text: row.text,
			kinds: (kindsBy.get(row.id) ?? []).sort(),
			gear: (gearBy.get(row.id) ?? []).sort(),
			tags: (tagsBy.get(row.id) ?? []).sort(),
			favorite: row.favorite,
			author: row.author
		};
		if (row.status === 'archived') card.status = 'archived';
		return card;
	});

	return buildExportYaml(exportCards);
}

export function exportFilename(now = new Date()): string {
	const iso = now.toISOString().slice(0, 10);
	return `sotao-${iso}.yaml`;
}
