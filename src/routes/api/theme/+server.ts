import { redirect } from '@sveltejs/kit';
import { setTheme } from '$lib/server/settings';
import { isTheme } from '$lib/theme';
import type { RequestHandler } from './$types';

/**
 * A plain form POST, so the theme switch works without JavaScript. The choice
 * lives in settings.theme and is applied to <html> in hooks.server.ts.
 */
export const POST: RequestHandler = async ({ request }) => {
	const form = await request.formData();
	const theme = form.get('theme');
	const from = form.get('from');

	if (!isTheme(theme)) redirect(303, '/draw?mode=single');
	await setTheme(theme);

	// Only ever bounce back to a path on this site.
	const target = typeof from === 'string' && from.startsWith('/') ? from : '/draw?mode=single';
	redirect(303, target);
};
