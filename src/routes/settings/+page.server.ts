import { redirect } from '@sveltejs/kit';
import { SESSION_COOKIE } from '$lib/server/auth';
import { getTheme, setTheme } from '$lib/server/settings';
import { isTheme } from '$lib/theme';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async () => {
	return { theme: await getTheme() };
};

export const actions: Actions = {
	theme: async ({ request }) => {
		const form = await request.formData();
		const theme = form.get('theme');
		if (isTheme(theme)) await setTheme(theme);
		return { saved: true };
	},

	// Deliberately here rather than in the header, where it would get hit by
	// accident (§10).
	logout: async ({ cookies }) => {
		cookies.delete(SESSION_COOKIE, { path: '/' });
		redirect(303, '/login');
	}
};
