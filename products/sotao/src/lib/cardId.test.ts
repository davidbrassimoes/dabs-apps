import { describe, expect, it } from 'vitest';
import { cardId, normalise } from './cardId';

// §14. Card ids are content-derived and persisted, so these tests are the
// contract: change what they assert and every stored id becomes wrong.

describe('normalise', () => {
	it('collapses whitespace, trims and lower-cases', () => {
		expect(normalise('  Mono.\n  All   of it.  ')).toBe('mono. all of it.');
	});

	it('composes Unicode to NFC', () => {
		// "é" written as a combining sequence must normalise to the single code point.
		expect(normalise('Djembés')).toBe(normalise('Djembés'));
		expect(normalise('Djembés')).toBe('djembés');
	});
});

describe('cardId', () => {
	it('is twelve lower-case hex characters', () => {
		expect(cardId('Mono. All of it.')).toMatch(/^[0-9a-f]{12}$/);
	});

	it('ignores case differences', () => {
		expect(cardId('MONO. ALL OF IT.')).toBe(cardId('mono. all of it.'));
	});

	it('ignores whitespace differences, including newlines and tabs', () => {
		expect(cardId('  Mono.   All of it.  ')).toBe(cardId('Mono. All of it.'));
		expect(cardId('Mono.\nAll of it.')).toBe(cardId('Mono. All of it.'));
		expect(cardId('Mono.\tAll of it.')).toBe(cardId('Mono. All of it.'));
	});

	it('ignores Unicode normalisation differences', () => {
		expect(cardId('Djembés, close-miked.')).toBe(cardId('Djembés, close-miked.'));
	});

	it('gives different text different ids', () => {
		expect(cardId('Mono. All of it.')).not.toBe(cardId('Nothing quantised.'));
		expect(cardId('Four tracks maximum.')).not.toBe(cardId('Five tracks maximum.'));
	});

	it('is stable across calls', () => {
		expect(cardId('Nothing quantised.')).toBe(cardId('Nothing quantised.'));
	});

	it('matches sha256(normalise(text)).slice(0, 12) exactly', () => {
		// Golden values computed independently from §5's definition with node's
		// own crypto. Stored ids depend on these staying put.
		expect(cardId('Mono. All of it.')).toBe('7579eb1ce9e8');
		expect(cardId('Djembés, close-miked.')).toBe('88bd5b19fdca');
		expect(cardId('Four tracks maximum.')).toBe('cb50fa7050ff');
	});

	it('handles an empty string without throwing', () => {
		expect(cardId('')).toMatch(/^[0-9a-f]{12}$/);
	});
});
