import { error, fail, redirect } from '@sveltejs/kit';
import { KINDS } from '$lib/server/db/schema';
import {
	allTags,
	cardExists,
	createCard,
	deleteCard,
	getCard,
	setCardStatus,
	updateCard
} from '$lib/server/cards';
import { listGear } from '$lib/server/gear';
import { parseCardForm } from '$lib/server/cardForm';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ params }) => {
	const isNew = params.id === 'new';

	const [gear, tags] = await Promise.all([listGear(), allTags()]);
	if (isNew) return { card: null, gear, tags };

	const card = await getCard(params.id, KINDS);
	if (!card) error(404, 'No card with that id.');

	return { card, gear, tags };
};

export const actions: Actions = {
	save: async ({ request, params }) => {
		const gearList = await listGear();
		const parsed = await parseCardForm(request, gearList);
		if (!parsed.ok) return fail(400, parsed.problem);

		const isNew = params.id === 'new';

		if (isNew) {
			// The id is derived from the text, so writing a card whose text already
			// exists would collide. Say so rather than failing on the primary key.
			const { cardId } = await import('$lib/cardId');
			const id = cardId(parsed.fields.text);
			if (await cardExists(id)) {
				return fail(409, {
					error: 'A card with that text already exists — open it instead of writing it twice.',
					values: parsed.values
				});
			}
			const created = await createCard(parsed.fields);
			redirect(303, `/cards/${created}`);
		}

		if (!(await cardExists(params.id))) error(404, 'No card with that id.');
		// The id stays as it was assigned. Editing the text does not move it (§5).
		await updateCard(params.id, parsed.fields, parsed.status);
		redirect(303, '/cards');
	},

	// Archive is the soft delete and the default action (§9).
	archive: async ({ params }) => {
		if (params.id === 'new') error(400, 'Nothing to archive yet.');
		await setCardStatus(params.id, 'archived');
		redirect(303, '/cards');
	},

	restore: async ({ params }) => {
		if (params.id === 'new') error(400, 'Nothing to restore.');
		await setCardStatus(params.id, 'active');
		redirect(303, `/cards/${params.id}`);
	},

	// Permanent, and it cascades through card_kinds, card_gear, card_tags and
	// draw_cards. The UI asks for confirmation before it gets here (§9).
	delete: async ({ params }) => {
		if (params.id === 'new') error(400, 'Nothing to delete yet.');
		await deleteCard(params.id);
		redirect(303, '/cards');
	}
};
