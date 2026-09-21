import { describe, expect, it } from 'vitest';
import { SPREAD_SLOTS, describeKinds, isSpreadSlot } from './kinds';

describe('spread slots', () => {
	it('is exactly the three §6 fixes, in order', () => {
		expect(SPREAD_SLOTS).toEqual(['source', 'treatment', 'constraint']);
	});

	it('does not count technique or arrangement as slots in phase 1', () => {
		expect(isSpreadSlot('technique')).toBe(false);
		expect(isSpreadSlot('arrangement')).toBe(false);
		expect(isSpreadSlot('source')).toBe(true);
	});
});

describe('describeKinds', () => {
	it('blocks an empty selection', () => {
		expect(describeKinds([])).toBe('Not drawable yet — pick at least one.');
	});

	it('describes a standalone card', () => {
		expect(describeKinds(['idea'])).toBe('Drawn on its own.');
	});

	it('describes a spread-only card', () => {
		expect(describeKinds(['source'])).toBe('Drawn into spreads only — the source slot.');
		expect(describeKinds(['source', 'constraint'])).toBe(
			'Drawn into spreads only — the source and constraint slots.'
		);
	});

	it('describes both together', () => {
		expect(describeKinds(['idea', 'source'])).toBe('Drawn on its own, and into the source slot.');
	});

	it('says plainly that technique alone is not drawable yet', () => {
		const sentence = describeKinds(['technique']);
		expect(sentence).toMatch(/not drawable yet/i);
		expect(sentence).toContain('technique');
	});

	it('does not claim a technique slot when idea is also picked', () => {
		const sentence = describeKinds(['idea', 'technique']);
		expect(sentence).toContain('Drawn on its own.');
		expect(sentence).not.toMatch(/into the technique slot/i);
	});

	it('names the real slots when inert kinds ride along', () => {
		const sentence = describeKinds(['source', 'arrangement']);
		expect(sentence).toContain('the source slot');
		expect(sentence).toMatch(/arrangement is a label only/i);
	});
});
