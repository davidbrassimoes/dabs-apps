import type { GearCategory } from '../src/lib/vocab';

// The inventory from §7, character for character. These slugs are the vocabulary
// card imports and the card-generation skill use, so they are not editable
// niceties — changing one breaks every card that references it.
//
// Always-on gear is deliberately absent: the AR16c, the ADAM T5V monitors,
// headphones and the laptop are always in the chain, so choosing them means
// nothing. Cards may name them in their text; there is no row and no reference.
// If asked to add a monitor here, push back before doing it.

export type SeedGear = {
	id: string;
	name: string;
	category: GearCategory;
	/** false is the wishlist: writable into cards, never drawable. */
	owned?: boolean;
};

export const SEED_GEAR: SeedGear[] = [
	// strings — 10 rows, 8 owned
	{ id: 'fender-strat', name: 'Fender Stratocaster (MX)', category: 'strings' },
	{ id: 'aria-strat', name: 'Aria strat', category: 'strings' },
	{ id: 'epiphone-sg', name: 'Epiphone SG', category: 'strings' },
	{ id: 'gretsch-12', name: 'Gretsch 12-string', category: 'strings' },
	{ id: 'nylon-classical', name: 'Nylon classical', category: 'strings' },
	{ id: 'bandolim', name: 'Bandolim', category: 'strings' },
	{ id: 'guitarra-portuguesa', name: 'Guitarra portuguesa', category: 'strings' },
	{ id: 'aria-bass', name: 'Aria bass', category: 'strings' },
	{ id: 'flamenca', name: 'Flamenca', category: 'strings', owned: false },
	{ id: 'steel-string', name: 'Steel-string acoustic', category: 'strings', owned: false },

	// keys — 7 rows
	{ id: 'rd-08', name: 'Roland RD-08', category: 'keys' },
	{ id: 'jv-880', name: 'Roland JV-880', category: 'keys' },
	{ id: 'mpc-one-plus', name: 'MPC One+', category: 'keys' },
	{ id: 'circuit-tracks', name: 'Circuit Tracks', category: 'keys' },
	{ id: 'circuit-rhythm', name: 'Circuit Rhythm', category: 'keys' },
	{ id: 'volca-keys', name: 'Volca Keys', category: 'keys' },
	{ id: 'sk-1', name: 'Casio SK-1', category: 'keys' },

	// amps — 3 rows
	{ id: 'ac30', name: 'Vox AC30', category: 'amps' },
	{ id: 'pathfinder-15r', name: 'Pathfinder 15R', category: 'amps' },
	{ id: 'pathfinder-bass-10', name: 'Pathfinder Bass 10', category: 'amps' },

	// pedals — 14 rows
	{ id: 'fuzz-factory', name: 'Fuzz Factory (custom)', category: 'pedals' },
	{ id: 'boss-fz5', name: 'Boss FZ-5', category: 'pedals' },
	{ id: 'french-toast', name: 'Danelectro French Toast', category: 'pedals' },
	{ id: 'hardwire-od', name: 'DigiTech Hardwire OD', category: 'pedals' },
	{ id: 'boss-phaser', name: 'Boss phaser', category: 'pedals' },
	{ id: 'eleclady', name: 'Mooer ElecLady', category: 'pedals' },
	{ id: 'pitch-fork-plus', name: 'EHX Pitch Fork+', category: 'pedals' },
	{ id: 'boss-volume', name: 'Boss volume pedal', category: 'pedals' },
	{ id: 'flashback-2', name: 'TC Flashback 2', category: 'pedals' },
	{ id: 'hall-of-fame-2', name: 'TC Hall of Fame 2', category: 'pedals' },
	{ id: 'synth-wah', name: 'DigiTech Synth Wah', category: 'pedals' },
	{ id: 'mooer-octaver', name: 'Mooer octaver', category: 'pedals' },
	{ id: 'artec-eq', name: 'Artec parametric EQ', category: 'pedals' },
	{ id: 'rp90', name: 'DigiTech RP90', category: 'pedals' },

	// mics — 6 rows, 3 owned
	{ id: 'xm8500', name: 'Behringer XM8500', category: 'mics' },
	{ id: 'cm25', name: 'Focusrite CM25 MkIII', category: 'mics' },
	{ id: 'h4essential', name: 'Zoom H4essential', category: 'mics' },
	{ id: 'sm57', name: 'Shure SM57', category: 'mics', owned: false },
	{ id: 'ga-r1', name: 'Golden Age R1 MkIII', category: 'mics', owned: false },
	{ id: 'c2-pair', name: 'Behringer C2 pair', category: 'mics', owned: false },

	// small — 7 rows
	{ id: 'harmonicas', name: 'Harmonicas', category: 'small' },
	{ id: 'melodica', name: 'Melodica', category: 'small' },
	{ id: 'djembes', name: 'Djembés', category: 'small' },
	{ id: 'kalimba', name: 'Kalimba', category: 'small' },
	{ id: 'flutes', name: 'Flutes', category: 'small' },
	{ id: 'ocarina', name: 'Ocarina', category: 'small' },
	{ id: 'the-tube', name: 'The tube', category: 'small' }
];
