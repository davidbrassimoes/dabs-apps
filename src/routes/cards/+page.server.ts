import { AUTHORS, CARD_STATUSES, KINDS } from '$lib/server/db/schema';
import type { Author, CardStatus, Kind } from '$lib/server/db/schema';
import { kindCounts, listCards, type CardFilters } from '$lib/server/cards';
import { listGear } from '$lib/server/gear';
import type { PageServerLoad } from './$types';

function pick<T extends string>(value: string | null, allowed: readonly T[]): T | null {
	return value && (allowed as readonly string[]).includes(value) ? (value as T) : null;
}

export const load: PageServerLoad = async ({ url }) => {
	// Filters live in the query string, so a filtered view is a link the owner
	// can keep, and the back button does what he expects.
	const params = url.searchParams;

	const filters: CardFilters = {
		search: params.get('q') ?? '',
		kinds: params.getAll('kind').filter((k): k is Kind => (KINDS as readonly string[]).includes(k)),
		author: pick<Author>(params.get('author'), AUTHORS),
		// No status param means active only, which is what the chip shows.
		status: params.has('status') ? pick<CardStatus>(params.get('status'), CARD_STATUSES) : 'active',
		gear: params.get('gear'),
		favouritesOnly: params.get('fav') === '1'
	};

	const [items, counts, gearList] = await Promise.all([
		listCards(filters, KINDS),
		kindCounts(),
		listGear()
	]);

	return { items, counts, filters, gear: gearList };
};
