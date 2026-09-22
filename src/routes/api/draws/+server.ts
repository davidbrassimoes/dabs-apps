import { error, json } from '@sveltejs/kit';
import { UnknownCardError, recordDraw, recentCardIds } from '$lib/server/draws';
import { drawRecordSchema } from '$lib/schemas';
import type { RequestHandler } from './$types';

/**
 * Records a completed draw. The client performed it; this only stores it.
 * Upserts on the client-minted uuid, so replaying a queued record does not
 * double-count draw_count (§6).
 */
export const POST: RequestHandler = async ({ request }) => {
	const parsed = drawRecordSchema.safeParse(await request.json().catch(() => null));
	if (!parsed.success) {
		error(400, parsed.error.issues[0]?.message ?? 'Not a draw record.');
	}

	try {
		const outcome = await recordDraw(parsed.data);
		return json({ outcome, recent: await recentCardIds() });
	} catch (e) {
		// A 4xx tells the client to drop the record instead of retrying forever.
		if (e instanceof UnknownCardError) error(409, e.message);
		throw e;
	}
};
