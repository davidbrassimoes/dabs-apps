import { eq } from 'drizzle-orm';
import { db } from './db';
import { settings } from './db/schema';
import { DEFAULT_THEME, isTheme, type Theme } from '$lib/theme';

// The settings table holds exactly three keys in phase 1 (§4): theme,
// drawFilters and schemaVersion. Values are JSON.

export const SCHEMA_VERSION = 1;

export type DrawFilters = {
	inRackOnly: boolean;
	favouritesOnly: boolean;
	tags: string[];
};

export const DEFAULT_DRAW_FILTERS: DrawFilters = {
	inRackOnly: true,
	favouritesOnly: false,
	tags: []
};

async function readSetting(key: string): Promise<unknown> {
	const [row] = await db.select().from(settings).where(eq(settings.key, key)).limit(1);
	if (!row) return undefined;
	try {
		return JSON.parse(row.value);
	} catch {
		return undefined;
	}
}

async function writeSetting(key: string, value: unknown): Promise<void> {
	await db
		.insert(settings)
		.values({ key, value: JSON.stringify(value) })
		.onConflictDoUpdate({ target: settings.key, set: { value: JSON.stringify(value) } });
}

export async function getTheme(): Promise<Theme> {
	const value = await readSetting('theme');
	return isTheme(value) ? value : DEFAULT_THEME;
}

export async function setTheme(theme: Theme): Promise<void> {
	await writeSetting('theme', theme);
}

export async function getDrawFilters(): Promise<DrawFilters> {
	const value = await readSetting('drawFilters');
	if (typeof value !== 'object' || value === null) return DEFAULT_DRAW_FILTERS;
	const raw = value as Partial<DrawFilters>;
	return {
		inRackOnly:
			typeof raw.inRackOnly === 'boolean' ? raw.inRackOnly : DEFAULT_DRAW_FILTERS.inRackOnly,
		favouritesOnly:
			typeof raw.favouritesOnly === 'boolean'
				? raw.favouritesOnly
				: DEFAULT_DRAW_FILTERS.favouritesOnly,
		tags: Array.isArray(raw.tags) ? raw.tags.filter((t): t is string => typeof t === 'string') : []
	};
}

export async function setDrawFilters(filters: DrawFilters): Promise<void> {
	await writeSetting('drawFilters', filters);
}

export async function ensureSchemaVersion(): Promise<void> {
	if ((await readSetting('schemaVersion')) === undefined) {
		await writeSetting('schemaVersion', SCHEMA_VERSION);
	}
}
