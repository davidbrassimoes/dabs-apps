import { rackCount } from '$lib/server/gear';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
	return {
		authenticated: locals.authenticated,
		theme: locals.theme,
		// The header chip reads "In the rack · N of 42" on every screen, so the
		// count is loaded here rather than by each page. /login has no header.
		rack: locals.authenticated ? await rackCount() : { inRack: 0, owned: 0 }
	};
};
