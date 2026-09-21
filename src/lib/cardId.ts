import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex } from '@noble/hashes/utils.js';

// Card identity (§5). Content-derived so that re-importing the same file is a
// no-op: the importer computes cardId(text), looks it up, and a hit means the
// card already exists.
//
// Pure, and the same on both sides — no node:crypto, which would not survive in
// the browser, and no crypto.subtle, which is async and would make the importer
// async all the way down.
//
// Two consequences the importer depends on, and which must not be "improved":
//
//   - Editing a card's text in the UI does NOT change its id. The id is
//     assigned once at creation. Store it; never recompute on update.
//   - Two texts differing only in case or whitespace are the same card.

export function normalise(text: string): string {
	return text.normalize('NFC').replace(/\s+/g, ' ').trim().toLowerCase();
}

export function cardId(text: string): string {
	return bytesToHex(sha256(new TextEncoder().encode(normalise(text)))).slice(0, 12);
}
