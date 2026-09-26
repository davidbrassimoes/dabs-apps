import { describe, expect, it } from 'vitest';
import { parseImport, type ExistingCardSnapshot } from './import';
import { buildExportYaml, type ExportCard } from './export';
import { cardId } from '$lib/cardId';

// §14's round-trip test: export → import produces zero changes. Exercised
// purely — buildExportYaml has no database in it, and re-importing is checked
// against an existingCards snapshot standing in for "this is already the
// database", which is what "produces zero changes" means in practice: every
// card the file describes is already there.

const KNOWN_GEAR = new Set(['toy-sampler', 'stage-piano', 'pitch-shifter', 'valve-combo', 'condenser']);

function toExisting(cards: ExportCard[]): Map<string, ExistingCardSnapshot> {
	const map = new Map<string, ExistingCardSnapshot>();
	for (const card of cards) {
		map.set(cardId(card.text), {
			kinds: card.kinds as ExistingCardSnapshot['kinds'],
			gear: card.gear,
			tags: card.tags,
			favorite: card.favorite
		});
	}
	return map;
}

const FIXTURE: ExportCard[] = [
	{
		text: 'Play the stage piano through the pitch shifter at plus one octave, reamp it into the valve combo, and mic the amp from the staircase opening.',
		kinds: ['idea'],
		gear: ['stage-piano', 'pitch-shifter', 'valve-combo', 'condenser'],
		tags: ['reamping', 'room'],
		favorite: false,
		author: 'claude'
	},
	{
		text: 'The toy sampler, sampled from something already in the room.',
		kinds: ['idea', 'source'],
		gear: ['toy-sampler'],
		tags: ['sampling', 'lo-fi'],
		favorite: true,
		author: 'david'
	},
	{
		text: 'Mono. All of it.',
		kinds: ['constraint'],
		gear: [],
		tags: ['mono'],
		favorite: true,
		author: 'claude',
		status: 'archived'
	}
];

describe('export → import round trip', () => {
	it('reports every card Unchanged, including an archived one', () => {
		const yaml = buildExportYaml(FIXTURE);
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: toExisting(FIXTURE) });

		expect(report.fileError).toBeNull();
		expect(report.counts).toEqual({ new: 0, updated: 0, unchanged: 3, error: 0 });
	});

	it('is stable under a second round trip', () => {
		const first = buildExportYaml(FIXTURE);
		const again = buildExportYaml(FIXTURE);
		expect(again).toBe(first);
	});

	it('never asks an existing card to change status via re-import', () => {
		// The exported card carries status: archived, but the diff never
		// compares status — an import must not be able to move a card between
		// active and archived on its own (§8).
		const yaml = buildExportYaml(FIXTURE);
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: toExisting(FIXTURE) });
		const archivedRow = report.rows.find((r) => r.text?.startsWith('Mono.'));
		expect(archivedRow?.class).toBe('unchanged');
		expect(archivedRow?.changed).toEqual([]);
	});
});
