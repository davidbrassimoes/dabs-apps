import { error, json } from '@sveltejs/kit';
import { existingCardSnapshots, knownGearSlugs, parseImport } from '$lib/server/import';
import type { RequestHandler } from './$types';

/** YAML text → the diff report. Writes nothing (§8). */
export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json().catch(() => null);
	const yamlText = body && typeof body === 'object' ? (body as { yaml?: unknown }).yaml : null;
	if (typeof yamlText !== 'string') error(400, 'Expected { yaml: string }.');

	const [knownGear, existingCards] = await Promise.all([knownGearSlugs(), existingCardSnapshots()]);
	const report = parseImport(yamlText, { knownGear, existingCards });

	return json(report);
};
