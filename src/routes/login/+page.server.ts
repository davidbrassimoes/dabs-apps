import { fail, redirect } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import {
	SESSION_COOKIE,
	SESSION_MAX_AGE,
	clearAttempts,
	constantTimeEqual,
	createSessionToken,
	isRateLimited,
	recordFailedAttempt
} from '$lib/server/auth';
import type { Actions, PageServerLoad } from './$types';

function safeRedirect(from: unknown): string {
	// Only ever follow a path on this site, never an absolute URL.
	return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')
		? from
		: '/draw?mode=single';
}

export const load: PageServerLoad = ({ locals, url }) => {
	if (locals.authenticated) redirect(303, safeRedirect(url.searchParams.get('from')));
	return { from: url.searchParams.get('from') ?? '' };
};

export const actions: Actions = {
	default: async ({ request, cookies, getClientAddress, url }) => {
		if (!env.APP_PASSWORD) throw new Error('APP_PASSWORD is not set');
		if (!env.APP_SECRET) throw new Error('APP_SECRET is not set');

		const ip = getClientAddress();
		if (isRateLimited(ip)) {
			return fail(429, { error: 'Too many attempts. Wait fifteen minutes.' });
		}

		const form = await request.formData();
		const password = form.get('password');

		if (typeof password !== 'string' || !constantTimeEqual(password, env.APP_PASSWORD)) {
			recordFailedAttempt(ip);
			return fail(401, { error: 'That is not the password.' });
		}

		clearAttempts(ip);
		cookies.set(SESSION_COOKIE, createSessionToken(env.APP_SECRET), {
			path: '/',
			httpOnly: true,
			secure: url.protocol === 'https:',
			sameSite: 'lax',
			maxAge: SESSION_MAX_AGE
		});

		redirect(303, safeRedirect(form.get('from')));
	}
};
