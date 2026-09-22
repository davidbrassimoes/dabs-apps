import { buildPool, tonightsDraws } from '$lib/server/draws';
import type { PageServerLoad } from './$types';

/**
 * The pool is sent with the page so the very first paint can draw, before the
 * client has fetched anything. After that the client works from its IndexedDB
 * copy and refreshes in the background — the draw itself never waits on the
 * network (§11).
 */
export const load: PageServerLoad = async () => {
	return {
		pool: await buildPool(),
		tonight: await tonightsDraws()
	};
};
