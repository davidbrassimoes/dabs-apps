// Theme choice. Escarpa Audio's two themes, Morning fog and Night fog, and the
// same three settings every product has (ADR-012, brand/tokens.md "Themes"):
// Auto follows the device and falls back to Morning where it cannot tell; a
// hand-picked theme wins over the device. Persisted in settings.theme and
// applied server-side on first render so the app never flashes the wrong
// ground (§12, as amended by ADR-027).

export const THEMES = ['auto', 'morning', 'night'] as const;
export type Theme = (typeof THEMES)[number];

export const DEFAULT_THEME: Theme = 'auto';

export function isTheme(value: unknown): value is Theme {
	return typeof value === 'string' && (THEMES as readonly string[]).includes(value);
}

// What settings.theme held before sea fog. Read, never written.
const LEGACY: Record<string, Theme> = { system: 'auto', light: 'morning', dark: 'night' };

/** A stored value as a theme: current names as they are, old ones mapped, anything else the default. */
export function readTheme(value: unknown): Theme {
	if (isTheme(value)) return value;
	if (typeof value === 'string' && value in LEGACY) return LEGACY[value];
	return DEFAULT_THEME;
}

/** The next theme in the cycle the header button steps through. */
export function nextTheme(current: Theme): Theme {
	return current === 'auto' ? 'morning' : current === 'morning' ? 'night' : 'auto';
}

export function themeLabel(theme: Theme): string {
	return theme === 'auto' ? 'Auto' : theme === 'morning' ? 'Morning' : 'Night';
}

/**
 * What goes on <html>. Auto resolves in CSS via prefers-color-scheme, so it
 * sets no attribute at all and lets the media query decide.
 */
export function themeAttribute(theme: Theme): string | null {
	return theme === 'auto' ? null : theme;
}
