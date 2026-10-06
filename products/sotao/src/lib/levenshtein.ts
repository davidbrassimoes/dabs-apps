// Edit distance, used by the importer to suggest a gear slug for a typo
// (§8: "Did you mean condenser?"). Small and pure enough to live in $lib and be
// tested on its own.

export function levenshtein(a: string, b: string): number {
	if (a === b) return 0;
	if (a.length === 0) return b.length;
	if (b.length === 0) return a.length;

	let previous = Array.from({ length: b.length + 1 }, (_, i) => i);
	for (let i = 1; i <= a.length; i++) {
		const current = [i];
		for (let j = 1; j <= b.length; j++) {
			const cost = a[i - 1] === b[j - 1] ? 0 : 1;
			current[j] = Math.min(
				current[j - 1] + 1, // insertion
				previous[j] + 1, // deletion
				previous[j - 1] + cost // substitution
			);
		}
		previous = current;
	}
	return previous[b.length];
}

/** The closest candidate within `maxDistance`, or null if none is close enough. */
export function nearest(
	target: string,
	candidates: Iterable<string>,
	maxDistance = 2
): string | null {
	let best: string | null = null;
	let bestDistance = Infinity;

	for (const candidate of candidates) {
		const distance = levenshtein(target, candidate);
		if (distance < bestDistance) {
			bestDistance = distance;
			best = candidate;
		}
	}

	return bestDistance <= maxDistance ? best : null;
}
