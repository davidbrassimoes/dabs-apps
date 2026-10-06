import { json } from '@sveltejs/kit';
import { buildPool } from '$lib/server/draws';
import type { RequestHandler } from './$types';

/**
 * Everything the client needs to draw (§11). A few hundred cards is a few tens
 * of kilobytes — not paginated, no deltas.
 */
export const GET: RequestHandler = async ({ setHeaders }) => {
	setHeaders({ 'cache-control': 'no-store' });
	return json(await buildPool());
};
