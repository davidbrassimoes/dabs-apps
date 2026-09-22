import { describe, expect, it } from 'vitest';
import { levenshtein, nearest } from './levenshtein';

describe('levenshtein', () => {
	it('is zero for identical strings', () => {
		expect(levenshtein('sm57', 'sm57')).toBe(0);
	});

	it('counts a single substitution', () => {
		expect(levenshtein('sm57', 'sm58')).toBe(1);
	});

	it('counts insertions and deletions', () => {
		expect(levenshtein('sm57', 'sm577')).toBe(1);
		expect(levenshtein('sm577', 'sm57')).toBe(1);
	});

	it('handles empty strings', () => {
		expect(levenshtein('', 'abc')).toBe(3);
		expect(levenshtein('abc', '')).toBe(3);
	});
});

describe('nearest', () => {
	const slugs = ['sm57', 'xm8500', 'cm25', 'h4essential'];

	it('suggests the closest slug within the distance', () => {
		expect(nearest('sm58', slugs)).toBe('sm57');
	});

	it('returns null when nothing is close enough', () => {
		expect(nearest('completely-different', slugs)).toBeNull();
	});

	it('respects a custom max distance', () => {
		expect(nearest('sm', slugs, 1)).toBeNull();
		expect(nearest('sm5', slugs, 1)).toBe('sm57');
	});
});
