// Theme choice. Three states: explicit dark, explicit light, follow the system.
// Persisted in settings.theme and applied server-side on first render so the
// app never flashes the wrong ground (§12).

export const THEMES = ['dark', 'light', 'system'] as const;
export type Theme = (typeof THEMES)[number];

export const DEFAULT_THEME: Theme = 'dark';

export function isTheme(value: unknown): value is Theme {
	return typeof value === 'string' && (THEMES as readonly string[]).includes(value);
}

/** The next theme in the cycle the header button steps through. */
export function nextTheme(current: Theme): Theme {
	return current === 'dark' ? 'light' : current === 'light' ? 'system' : 'dark';
}

export function themeLabel(theme: Theme): string {
	return theme === 'system' ? 'System' : theme === 'dark' ? 'Dark' : 'Light';
}

/**
 * What goes on <html>. `system` resolves in CSS via prefers-color-scheme, so it
 * sets no attribute at all and lets the media query decide.
 */
export function themeAttribute(theme: Theme): string | null {
	return theme === 'system' ? null : theme;
}
