import type { SeedGear } from './types';

// The committed inventory: DABS's rig from §7, described generically. The real
// one names real products, and rule H1 keeps real product and manufacturer
// names out of the repository (ADR-027), so it lives in gear.local.ts, which is
// gitignored. seed/index.ts uses that file when it is there, and this one when
// it is not: in CI, and in a fresh clone.
//
// Same shape as the real list, row for row — 47 rows, the same per-category
// counts, five on the wishlist — so the tests that pin §7's shape hold for both,
// and a clone without the local file still behaves like the real app.
//
// Always-on gear is deliberately absent: the desk, the monitors, headphones and
// the laptop are always in the chain, so choosing them means nothing. Cards may
// name them in their text; there is no row and no reference. If asked to add a
// monitor here, push back before doing it.

export const SEED_GEAR: SeedGear[] = [
	// strings — 10 rows, 8 owned
	{ id: 'single-coil', name: 'Single-coil electric', category: 'strings' },
	{ id: 'spare-single-coil', name: 'Spare single-coil electric', category: 'strings' },
	{ id: 'humbucker', name: 'Humbucker electric', category: 'strings' },
	{ id: 'twelve-string', name: '12-string', category: 'strings' },
	{ id: 'nylon-classical', name: 'Nylon classical', category: 'strings' },
	{ id: 'bandolim', name: 'Bandolim', category: 'strings' },
	{ id: 'guitarra-portuguesa', name: 'Guitarra portuguesa', category: 'strings' },
	{ id: 'electric-bass', name: 'Electric bass', category: 'strings' },
	{ id: 'flamenca', name: 'Flamenca', category: 'strings', owned: false },
	{ id: 'steel-string', name: 'Steel-string acoustic', category: 'strings', owned: false },

	// keys — 7 rows
	{ id: 'stage-piano', name: 'Stage piano', category: 'keys' },
	{ id: 'rack-synth', name: 'Rack synth module', category: 'keys' },
	{ id: 'pad-sampler', name: 'Pad sampler', category: 'keys' },
	{ id: 'synth-groovebox', name: 'Synth groovebox', category: 'keys' },
	{ id: 'drum-groovebox', name: 'Sampling drum groovebox', category: 'keys' },
	{ id: 'mini-synth', name: 'Mini analogue synth', category: 'keys' },
	{ id: 'toy-sampler', name: 'Toy sampling keyboard', category: 'keys' },

	// amps — 3 rows
	{ id: 'valve-combo', name: '2×12 valve combo', category: 'amps' },
	{ id: 'practice-amp', name: '15 W practice combo', category: 'amps' },
	{ id: 'bass-practice-amp', name: '10 W bass combo', category: 'amps' },

	// pedals — 14 rows
	{ id: 'fuzz', name: 'Fuzz (custom)', category: 'pedals' },
	{ id: 'compact-fuzz', name: 'Compact fuzz', category: 'pedals' },
	{ id: 'octave-fuzz', name: 'Octave fuzz', category: 'pedals' },
	{ id: 'overdrive', name: 'Overdrive', category: 'pedals' },
	{ id: 'phaser', name: 'Phaser', category: 'pedals' },
	{ id: 'flanger', name: 'Flanger', category: 'pedals' },
	{ id: 'pitch-shifter', name: 'Pitch shifter', category: 'pedals' },
	{ id: 'volume-pedal', name: 'Volume pedal', category: 'pedals' },
	{ id: 'delay', name: 'Delay', category: 'pedals' },
	{ id: 'reverb', name: 'Reverb', category: 'pedals' },
	{ id: 'envelope-filter', name: 'Envelope filter', category: 'pedals' },
	{ id: 'octaver', name: 'Octaver', category: 'pedals' },
	{ id: 'parametric-eq', name: 'Parametric EQ', category: 'pedals' },
	{ id: 'multi-effect', name: 'Multi-effect', category: 'pedals' },

	// mics — 6 rows, 3 owned
	{ id: 'dynamic-mic', name: 'Dynamic vocal mic', category: 'mics' },
	{ id: 'condenser', name: 'Large-diaphragm condenser', category: 'mics' },
	{ id: 'handheld-recorder', name: 'Handheld recorder', category: 'mics' },
	{ id: 'instrument-dynamic', name: 'Instrument dynamic', category: 'mics', owned: false },
	{ id: 'ribbon', name: 'Ribbon mic', category: 'mics', owned: false },
	{ id: 'pencil-pair', name: 'Pencil condenser pair', category: 'mics', owned: false },

	// small — 7 rows
	{ id: 'harmonicas', name: 'Harmonicas', category: 'small' },
	{ id: 'melodica', name: 'Melodica', category: 'small' },
	{ id: 'djembes', name: 'Djembés', category: 'small' },
	{ id: 'kalimba', name: 'Kalimba', category: 'small' },
	{ id: 'flutes', name: 'Flutes', category: 'small' },
	{ id: 'ocarina', name: 'Ocarina', category: 'small' },
	{ id: 'the-tube', name: 'The tube', category: 'small' }
];
