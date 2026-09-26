import { describe, expect, it } from 'vitest';
import { DEFAULT_THEME, nextTheme, readTheme, themeAttribute } from './theme';

describe('the theme setting (ADR-012)', () => {
	it('defaults to Auto', () => {
		expect(DEFAULT_THEME).toBe('auto');
		expect(readTheme(undefined)).toBe('auto');
		expect(readTheme('sepia')).toBe('auto');
	});

	it('reads what was stored before sea fog', () => {
		expect(readTheme('system')).toBe('auto');
		expect(readTheme('light')).toBe('morning');
		expect(readTheme('dark')).toBe('night');
	});

	it('steps Auto → Morning → Night and round again', () => {
		expect(nextTheme('auto')).toBe('morning');
		expect(nextTheme('morning')).toBe('night');
		expect(nextTheme('night')).toBe('auto');
	});

	it('leaves Auto to the media query and pins the other two', () => {
		expect(themeAttribute('auto')).toBeNull();
		expect(themeAttribute('morning')).toBe('morning');
		expect(themeAttribute('night')).toBe('night');
	});
});
