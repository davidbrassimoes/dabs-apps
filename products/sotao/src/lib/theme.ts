// Theme choice. Two themes, Dark and Light, and three settings: Auto follows
// the device and falls back to Dark where it cannot tell (the Draw screen is
// used in a dark room); a hand-picked theme wins over the device. Persisted in
// settings.theme and applied server-side on first render so the app never
// flashes the wrong ground.

export const THEMES = ['auto', 'dark', 'light'] as const;
export type Theme = (typeof THEMES)[number];

export const DEFAULT_THEME: Theme = 'auto';

export function isTheme(value: unknown): value is Theme {
	return typeof value === 'string' && (THEMES as readonly string[]).includes(value);
}

// What settings.theme held under earlier looks. Read, never written.
const LEGACY: Record<string, Theme> = {
	system: 'auto',
	night: 'dark',
	morning: 'light'
};

/** A stored value as a theme: current names as they are, old ones mapped, anything else the default. */
export function readTheme(value: unknown): Theme {
	if (isTheme(value)) return value;
	if (typeof value === 'string' && value in LEGACY) return LEGACY[value];
	return DEFAULT_THEME;
}

/** The next theme in the cycle the header button steps through. */
export function nextTheme(current: Theme): Theme {
	return current === 'auto' ? 'dark' : current === 'dark' ? 'light' : 'auto';
}

export function themeLabel(theme: Theme): string {
	return theme === 'auto' ? 'Auto' : theme === 'dark' ? 'Dark' : 'Light';
}

/**
 * What goes on <html>. Auto resolves in CSS (Dark, unless the device asks for
 * light), so it sets no attribute at all.
 */
export function themeAttribute(theme: Theme): string | null {
	return theme === 'auto' ? null : theme;
}
