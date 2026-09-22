import { error, json } from '@sveltejs/kit';
import { z } from 'zod';
import { setFavorite } from '$lib/server/cards';
import type { RequestHandler } from './$types';

const bodySchema = z.object({
	id: z.string().regex(/^[0-9a-f]{12}$/),
	favorite: z.boolean()
});

/**
 * The draw screen's F key and the favourite button. A marker only — it has no
 * effect on draw odds (§2), so nothing here touches the pool's selection.
 */
export const POST: RequestHandler = async ({ request }) => {
	const parsed = bodySchema.safeParse(await request.json().catch(() => null));
	if (!parsed.success) error(400, 'Not a favourite change.');

	await setFavorite(parsed.data.id, parsed.data.favorite);
	return json({ ok: true });
};
