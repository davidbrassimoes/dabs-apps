// The shared vocabulary: the closed sets the data model, the UI and the
// importer all speak.
//
// It lives here rather than in server/db/schema.ts because the client needs it
// too — the editor's kind checkboxes, the manager's filters and draw.ts in M3.
// Nothing under server/ may reach the browser bundle (§3), so the Drizzle
// schema imports these rather than owning them.

export const KINDS = [
	'idea',
	'source',
	'treatment',
	'technique',
	'constraint',
	'arrangement'
] as const;
export type Kind = (typeof KINDS)[number];

export const GEAR_CATEGORIES = ['strings', 'keys', 'amps', 'pedals', 'mics', 'small'] as const;
export type GearCategory = (typeof GEAR_CATEGORIES)[number];

export const AUTHORS = ['david', 'claude'] as const;
export type Author = (typeof AUTHORS)[number];

export const CARD_STATUSES = ['active', 'archived'] as const;
export type CardStatus = (typeof CARD_STATUSES)[number];

export const DRAW_MODES = ['single', 'spread'] as const;
export type DrawMode = (typeof DRAW_MODES)[number];
