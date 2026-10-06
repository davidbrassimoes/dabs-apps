import { createClient } from '@libsql/client';
import { drizzle } from 'drizzle-orm/libsql';
import { building } from '$app/environment';
import { env } from '$env/dynamic/private';
import { applyMigrations } from './migrations';
import * as schema from './schema';

// `vite build` imports server modules to analyse them, with no secrets set and
// no query run. Only then does the client get a placeholder; a server that
// starts without DATABASE_URL still stops here.
const url = building ? ':memory:' : env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL is not set');

const client = createClient({
	url,
	authToken: env.DATABASE_AUTH_TOKEN || undefined
});

export const db = drizzle(client, { schema });

let ready: Promise<void> | undefined;

/**
 * Runs pending migrations once per process (per isolate, on a Worker), turns foreign keys on, and seeds
 * the §7 gear inventory and Appendix B's starter cards if their tables are
 * empty.
 *
 * SQLite defaults `foreign_keys` to off, which would quietly disable both the
 * cascades on card_kinds/card_gear/card_tags and the RESTRICT that stops gear
 * being deleted while cards still reference it. Migrations run with it off
 * because drizzle-kit rebuilds tables, so the pragma goes on afterwards.
 *
 * The migrations are bundled rather than read from drizzle/, because a Worker
 * has no filesystem; see ./migrations.ts.
 */
export function migrateDb(): Promise<void> {
	ready ??= (async () => {
		await applyMigrations(client);
		await client.execute('PRAGMA foreign_keys = ON');
		const { seedGearIfEmpty, seedCardsIfEmpty } = await import('./seed');
		await seedGearIfEmpty();
		// Cards after gear: the starter set references gear slugs by id.
		await seedCardsIfEmpty();
	})();
	return ready;
}
