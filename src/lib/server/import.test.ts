import { describe, expect, it } from 'vitest';
import { parseImport, type ExistingCardSnapshot } from './import';
import { cardId } from '$lib/cardId';

// §14's list for import.ts. parseImport is pure with respect to the database:
// it takes the YAML text plus the inventory and existing-card state as plain
// data, and returns a report. That is what makes "importing twice reports
// Unchanged" testable without spinning up a database — the second run is
// simulated by feeding the first run's rows back in as existingCards.

const KNOWN_GEAR = new Set(['sk-1', 'sm57', 'cm25', 'rd-08', 'ac30']);

function noExisting(): Map<string, ExistingCardSnapshot> {
	return new Map();
}

describe('a valid file', () => {
	const yaml = `
version: 1
defaults:
  author: claude
cards:
  - text: Casio SK-1, sampled from something already in the room.
    kinds: [idea, source]
    gear: [sk-1]
    tags: [sampling, lo-fi]
    favorite: true
  - text: Mono. All of it.
    kinds: [constraint]
`;

	it('imports with the right counts', () => {
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.fileError).toBeNull();
		expect(report.counts).toEqual({ new: 2, updated: 0, unchanged: 0, error: 0 });
		expect(report.rows).toHaveLength(2);
	});

	it('resolves the id the same way cardId.ts does', () => {
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.rows[0].id).toBe(
			cardId('Casio SK-1, sampled from something already in the room.')
		);
	});

	it('merges defaults.author onto a card with no author of its own', () => {
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.rows[0].author).toBe('claude');
	});

	it('carries kinds, gear, tags and favorite through', () => {
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.rows[0].kinds).toEqual(['idea', 'source']);
		expect(report.rows[0].gear).toEqual(['sk-1']);
		expect(report.rows[0].tags.sort()).toEqual(['lo-fi', 'sampling']);
		expect(report.rows[0].favorite).toBe(true);
	});
});

describe('an unknown gear slug', () => {
	const yaml = `
version: 1
cards:
  - text: Mic the SM58 on the cabinet, off-axis by 45 degrees.
    kinds: [idea]
    gear: [sm58]
    author: claude
`;

	it('produces an error with the right line number and a suggestion', () => {
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.counts.error).toBe(1);

		const row = report.rows[0];
		expect(row.class).toBe('error');
		expect(row.issue?.cardIndex).toBe(1);
		// The fixture's leading blank line (from the template literal) makes
		// `    gear: [sm58]` line 6, not 5.
		expect(row.issue?.line).toBe(6);
		expect(row.issue?.message).toContain('sm58');
		expect(row.issue?.message).toContain('sm57');
	});
});

describe('an unknown key', () => {
	it('is an error, not a silent drop', () => {
		const yaml = `
version: 1
cards:
  - text: Mono. All of it.
    kind: [constraint]
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.counts.error).toBe(1);
		expect(report.rows[0].class).toBe('error');
		expect(report.rows[0].issue?.message).toMatch(/kind/i);
		// The typo must not be silently treated as an empty-kinds card.
		expect(report.rows[0].kinds).not.toContain('constraint');
	});

	it('rejects an unknown key on the defaults block', () => {
		const yaml = `
version: 1
defaults:
  authro: claude
cards:
  - text: Mono. All of it.
    kinds: [constraint]
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.fileError).toBeTruthy();
	});

	it('rejects an unknown top-level key', () => {
		const yaml = `
version: 1
extra: nope
cards:
  - text: Mono. All of it.
    kinds: [constraint]
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.fileError).toBeTruthy();
	});
});

describe('re-importing the same file', () => {
	const yaml = `
version: 1
defaults:
  author: claude
cards:
  - text: Mono. All of it.
    kinds: [constraint]
    tags: [mono]
    favorite: true
  - text: Nothing quantised.
    kinds: [constraint]
`;

	/** Turns a report's rows into the existingCards a second run would see. */
	function asExisting(
		rows: ReturnType<typeof parseImport>['rows']
	): Map<string, ExistingCardSnapshot> {
		const map = new Map<string, ExistingCardSnapshot>();
		for (const row of rows) {
			if (row.id) {
				map.set(row.id, {
					kinds: row.kinds,
					gear: row.gear,
					tags: row.tags,
					favorite: row.favorite
				});
			}
		}
		return map;
	}

	it('reports all Unchanged the second time, with zero writes implied', () => {
		const first = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(first.counts.new).toBe(2);

		const second = parseImport(yaml, {
			knownGear: KNOWN_GEAR,
			existingCards: asExisting(first.rows)
		});

		expect(second.counts).toEqual({ new: 0, updated: 0, unchanged: 2, error: 0 });
		// Unchanged rows carry nothing that would tell a caller to write.
		expect(second.rows.every((r) => r.class === 'unchanged')).toBe(true);
	});

	it("changing one card's tags and re-importing reports exactly one Updated", () => {
		const first = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		const existing = asExisting(first.rows);

		const changed = yaml.replace('tags: [mono]', 'tags: [mono, silence]');
		const second = parseImport(changed, { knownGear: KNOWN_GEAR, existingCards: existing });

		expect(second.counts).toEqual({ new: 0, updated: 1, unchanged: 1, error: 0 });
		const updatedRow = second.rows.find((r) => r.class === 'updated');
		expect(updatedRow?.tags).toEqual(['mono', 'silence']);
	});

	it('editing the card text produces a New row, not an Updated one', () => {
		// Text is the identity (§5) — a different id, so this is a different
		// card as far as import is concerned, however similar the wording.
		const first = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		const existing = asExisting(first.rows);

		const changed = yaml.replace('Mono. All of it.', 'Mono. All of it, always.');
		const second = parseImport(changed, { knownGear: KNOWN_GEAR, existingCards: existing });

		expect(second.counts.new).toBe(1);
		expect(second.counts.unchanged).toBe(1);
	});
});

describe('one bad card among good ones', () => {
	const yaml = `
version: 1
defaults:
  author: claude
cards:
  - text: Mono. All of it.
    kinds: [constraint]
  - text: Nothing quantised.
    kinds: [constraint]
  - text: Four tracks maximum.
    kinds: [constraint]
  - text: Uses gear nobody owns.
    kinds: [idea]
    gear: [does-not-exist]
  - text: One microphone. Move it instead of reaching for another.
    kinds: [constraint, technique]
`;

	it('imports the four good cards and reports exactly one error', () => {
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.counts.new).toBe(4);
		expect(report.counts.error).toBe(1);
		expect(report.rows).toHaveLength(5);
	});
});

describe('field validation', () => {
	it('rejects a card with zero kinds', () => {
		const yaml = `
version: 1
cards:
  - text: A card with nothing to be.
    kinds: []
    author: claude
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.counts.error).toBe(1);
	});

	it('rejects text over 500 characters', () => {
		const yaml = `
version: 1
cards:
  - text: "${'x'.repeat(501)}"
    kinds: [idea]
    author: claude
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.counts.error).toBe(1);
	});

	it('rejects an unknown kind', () => {
		const yaml = `
version: 1
cards:
  - text: A card with a made-up kind.
    kinds: [vibe]
    author: claude
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.counts.error).toBe(1);
	});

	it("merges defaults.tags with a card's own tags", () => {
		const yaml = `
version: 1
defaults:
  author: claude
  tags: [room]
cards:
  - text: Mono. All of it.
    kinds: [constraint]
    tags: [mono]
`;
		const report = parseImport(yaml, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.rows[0].tags.sort()).toEqual(['mono', 'room']);
	});
});

describe('file-level failure', () => {
	it('reports a syntax error rather than crashing', () => {
		const broken = 'version: 1\ncards:\n  - text: [unterminated\n';
		const report = parseImport(broken, { knownGear: KNOWN_GEAR, existingCards: noExisting() });
		expect(report.fileError).toBeTruthy();
		expect(report.rows).toHaveLength(0);
	});

	it('reports a missing cards array', () => {
		const report = parseImport('version: 1\n', {
			knownGear: KNOWN_GEAR,
			existingCards: noExisting()
		});
		expect(report.fileError).toBeTruthy();
	});

	it('reports a wrong version', () => {
		const report = parseImport('version: 2\ncards: []\n', {
			knownGear: KNOWN_GEAR,
			existingCards: noExisting()
		});
		expect(report.fileError).toBeTruthy();
	});
});
