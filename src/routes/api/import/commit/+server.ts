import { error, json } from '@sveltejs/kit';
import {
	applyImport,
	existingCardSnapshots,
	knownGearSlugs,
	parseImport
} from '$lib/server/import';
import type { RequestHandler } from './$types';

/**
 * YAML text → transactional insert/update (§8). Re-validates server-side
 * rather than trusting a client-supplied preview: the inventory or the
 * database may have moved between preview and commit, and nothing here
 * should write what was never freshly checked.
 */
export const POST: RequestHandler = async ({ request }) => {
	const body = await request.json().catch(() => null);
	const yamlText = body && typeof body === 'object' ? (body as { yaml?: unknown }).yaml : null;
	if (typeof yamlText !== 'string') error(400, 'Expected { yaml: string }.');

	const [knownGear, existingCards] = await Promise.all([knownGearSlugs(), existingCardSnapshots()]);
	const report = parseImport(yamlText, { knownGear, existingCards });

	if (report.fileError) error(400, report.fileError);

	const summary = await applyImport(report.rows);
	return json({ summary, counts: report.counts });
};
