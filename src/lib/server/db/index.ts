import { createClient } from '@libsql/client';
import { drizzle } from 'drizzle-orm/libsql';
import { migrate } from 'drizzle-orm/libsql/migrator';
import { env } from '$env/dynamic/private';
import * as schema from './schema';

if (!env.DATABASE_URL) throw new Error('DATABASE_URL is not set');

const client = createClient({
	url: env.DATABASE_URL,
	authToken: env.DATABASE_AUTH_TOKEN || undefined
});

export const db = drizzle(client, { schema });

let ready: Promise<void> | undefined;

/**
 * Runs pending migrations once per process, turns foreign keys on, and seeds
 * the §7 gear inventory and Appendix B's starter cards if their tables are
 * empty.
 *
 * SQLite defaults `foreign_keys` to off, which would quietly disable both the
 * cascades on card_kinds/card_gear/card_tags and the RESTRICT that stops gear
 * being deleted while cards still reference it. Migrations run with it off
 * because drizzle-kit rebuilds tables, so the pragma goes on afterwards.
 */
export function migrateDb(): Promise<void> {
	ready ??= (async () => {
		await migrate(db, { migrationsFolder: './drizzle' });
		await client.execute('PRAGMA foreign_keys = ON');
		const { seedGearIfEmpty, seedCardsIfEmpty } = await import('./seed');
		await seedGearIfEmpty();
		// Cards after gear: the starter set references gear slugs by id.
		await seedCardsIfEmpty();
	})();
	return ready;
}
