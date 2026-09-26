import type { GearCategory } from '../src/lib/vocab';

export type SeedGear = {
	id: string;
	name: string;
	category: GearCategory;
	/** false is the wishlist: writable into cards, never drawable. */
	owned?: boolean;
};
