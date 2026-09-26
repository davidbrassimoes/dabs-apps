import { describe, expect, it } from 'vitest';
import { levenshtein, nearest } from './levenshtein';

describe('levenshtein', () => {
	it('is zero for identical strings', () => {
		expect(levenshtein('ab57', 'ab57')).toBe(0);
	});

	it('counts a single substitution', () => {
		expect(levenshtein('ab57', 'ab58')).toBe(1);
	});

	it('counts insertions and deletions', () => {
		expect(levenshtein('ab57', 'ab577')).toBe(1);
		expect(levenshtein('ab577', 'ab57')).toBe(1);
	});

	it('handles empty strings', () => {
		expect(levenshtein('', 'abc')).toBe(3);
		expect(levenshtein('abc', '')).toBe(3);
	});
});

describe('nearest', () => {
	const slugs = ['ab57', 'dynamic-mic', 'condenser', 'handheld-recorder'];

	it('suggests the closest slug within the distance', () => {
		expect(nearest('ab58', slugs)).toBe('ab57');
	});

	it('returns null when nothing is close enough', () => {
		expect(nearest('completely-different', slugs)).toBeNull();
	});

	it('respects a custom max distance', () => {
		expect(nearest('ab', slugs, 1)).toBeNull();
		expect(nearest('ab5', slugs, 1)).toBe('ab57');
	});
});
