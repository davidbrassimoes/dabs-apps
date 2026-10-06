import { fail } from '@sveltejs/kit';
import { GEAR_CATEGORIES, type GearCategory } from '$lib/server/db/schema';
import {
	addGear,
	cardsUsingGear,
	gearExists,
	listGear,
	rackCount,
	setAllInRack,
	setInRack,
	setOwned
} from '$lib/server/gear';
import { isValidSlug } from '$lib/gear';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async () => {
	const [items, rack] = await Promise.all([listGear(), rackCount()]);
	return { items, rack };
};

function isCategory(value: unknown): value is GearCategory {
	return typeof value === 'string' && (GEAR_CATEGORIES as readonly string[]).includes(value);
}

export const actions: Actions = {
	// One checkbox. The form posts on change; without JavaScript it posts on submit.
	toggle: async ({ request }) => {
		const form = await request.formData();
		const id = form.get('id');
		if (typeof id !== 'string') return fail(400, { error: 'Missing gear.' });
		await setInRack(id, form.get('inRack') === 'on');
		return { ok: true };
	},

	// "Everything" and "Clear".
	bulk: async ({ request }) => {
		const form = await request.formData();
		await setAllInRack(form.get('value') === 'on');
		return { ok: true };
	},

	// Flipping a wishlist item to owned brings its cards to life. The notice
	// carries an undo, because the badge is the only control and a mis-click
	// would otherwise be a one-way trip — the screen draws no way back.
	owned: async ({ request }) => {
		const form = await request.formData();
		const id = form.get('id');
		if (typeof id !== 'string') return fail(400, { error: 'Missing gear.' });

		const owned = form.get('owned') === 'on';
		await setOwned(id, owned);

		if (owned) {
			return { notice: `“${id}” is now owned and can be racked.`, undo: id };
		}

		const used = await cardsUsingGear(id);
		return {
			notice:
				used > 0
					? `“${id}” is back on the wishlist. ${used} card${used === 1 ? '' : 's'} referencing it will stay out of draws.`
					: `“${id}” is back on the wishlist.`
		};
	},

	add: async ({ request }) => {
		const form = await request.formData();
		const name = String(form.get('name') ?? '').trim();
		// Case is normalised rather than rejected — "Boss-DD-7" becomes
		// "boss-dd-7" — so the stored vocabulary always matches §7's pattern.
		// Everything else about the shape is an error the owner has to fix.
		const id = String(form.get('id') ?? '')
			.trim()
			.toLowerCase();
		const category = form.get('category');
		const owned = form.get('owned') === 'on';

		const values = { name, id, category: String(category ?? ''), owned };

		if (!name) return fail(400, { error: 'Give it a name.', values });
		if (!id) return fail(400, { error: 'Give it a slug.', values });
		if (!isValidSlug(id)) {
			return fail(400, {
				error: 'A slug is lower-case letters, digits and hyphens, starting with a letter or digit.',
				values
			});
		}
		if (!isCategory(category)) return fail(400, { error: 'Pick a category.', values });
		if (await gearExists(id)) {
			return fail(409, { error: `“${id}” is already in the inventory.`, values });
		}

		await addGear({ id, name, category, owned });
		return { added: id };
	}
};
