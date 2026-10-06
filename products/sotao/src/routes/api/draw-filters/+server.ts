import { error, json } from '@sveltejs/kit';
import { z } from 'zod';
import { setDrawFilters } from '$lib/server/settings';
import type { RequestHandler } from './$types';

const bodySchema = z.object({
	inRackOnly: z.boolean(),
	favouritesOnly: z.boolean(),
	tags: z.array(z.string())
});

/** Filters are edited from the draw screen and applied to every draw (§6). */
export const POST: RequestHandler = async ({ request }) => {
	const parsed = bodySchema.safeParse(await request.json().catch(() => null));
	if (!parsed.success) error(400, 'Not a valid filter set.');

	await setDrawFilters(parsed.data);
	return json({ ok: true });
};
