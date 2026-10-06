import { exportFilename, exportYaml } from '$lib/server/export';
import type { RequestHandler } from './$types';

/** Every card, including archived ones, in exactly the import format (§8). */
export const GET: RequestHandler = async () => {
	const yaml = await exportYaml();
	return new Response(yaml, {
		headers: {
			'content-type': 'application/yaml; charset=utf-8',
			'content-disposition': `attachment; filename="${exportFilename()}"`
		}
	});
};
