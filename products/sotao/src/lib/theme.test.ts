import { describe, expect, it } from 'vitest';
import { DEFAULT_THEME, nextTheme, readTheme, themeAttribute } from './theme';

describe('the theme setting', () => {
	it('defaults to Auto', () => {
		expect(DEFAULT_THEME).toBe('auto');
		expect(readTheme(undefined)).toBe('auto');
		expect(readTheme('sepia')).toBe('auto');
	});

	it('reads the current names as they are', () => {
		expect(readTheme('auto')).toBe('auto');
		expect(readTheme('dark')).toBe('dark');
		expect(readTheme('light')).toBe('light');
	});

	it('maps what was stored under earlier looks', () => {
		expect(readTheme('system')).toBe('auto');
		expect(readTheme('night')).toBe('dark');
		expect(readTheme('morning')).toBe('light');
	});

	it('steps Auto → Dark → Light and round again', () => {
		expect(nextTheme('auto')).toBe('dark');
		expect(nextTheme('dark')).toBe('light');
		expect(nextTheme('light')).toBe('auto');
	});

	it('leaves Auto to the media query and pins the other two', () => {
		expect(themeAttribute('auto')).toBeNull();
		expect(themeAttribute('dark')).toBe('dark');
		expect(themeAttribute('light')).toBe('light');
	});
});
