import { redirect, type Handle } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { SESSION_COOKIE, verifySessionToken } from '$lib/server/auth';
import { migrateDb } from '$lib/server/db';
import { ensureSchemaVersion, getTheme } from '$lib/server/settings';
import { DEFAULT_THEME, themeAttribute } from '$lib/theme';

// Everything but /login and static assets is behind the cookie (§10).
const PUBLIC_ROUTES = ['/login'];

function isPublic(pathname: string): boolean {
	return PUBLIC_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

export const handle: Handle = async ({ event, resolve }) => {
	await migrateDb();
	await ensureSchemaVersion();

	if (!env.APP_SECRET) throw new Error('APP_SECRET is not set');

	const token = event.cookies.get(SESSION_COOKIE);
	event.locals.authenticated = Boolean(token && verifySessionToken(token, env.APP_SECRET));

	if (!event.locals.authenticated && !isPublic(event.url.pathname)) {
		const from = event.url.pathname + event.url.search;
		redirect(303, `/login?from=${encodeURIComponent(from)}`);
	}

	// The theme is applied to <html> here so the first paint is already right
	// and the app never flashes the wrong ground (§12).
	const theme = event.locals.authenticated ? await getTheme() : DEFAULT_THEME;
	event.locals.theme = theme;

	return resolve(event, {
		// Read when the page is rendered, not before: a form action that changes
		// the theme sets locals.theme, and its own response must show the change.
		transformPageChunk: ({ html }) => {
			const attribute = themeAttribute(event.locals.theme);
			return html.replace('%sotao.theme%', attribute ? ` data-theme="${attribute}"` : '');
		}
	});
};
