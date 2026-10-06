import type { D1Database } from '@cloudflare/workers-types';
import type { LibSQLDatabase } from 'drizzle-orm/libsql';
import type { BaseSQLiteDatabase } from 'drizzle-orm/sqlite-core';
import { drizzle as drizzleD1 } from 'drizzle-orm/d1';
import { env } from '$env/dynamic/private';
import { applyMigrations, d1Target, libsqlTarget, type MigrationTarget } from './migrations';
import * as schema from './schema';

/**
 * The one database type the app sees: drizzle's async SQLite, plus `batch`,
 * which both drivers have and which is how the app writes atomically (D1 has
 * no transactions).
 */
export type Db = BaseSQLiteDatabase<'async', unknown, typeof schema> &
	Pick<LibSQLDatabase<typeof schema>, 'batch'>;

// Which driver backs `db` is only known on the first request: D1 arrives as a
// binding on the request's platform, libSQL is opened from DATABASE_URL (tests).
// So every importer keeps `import { db }`, and `db` is a thin proxy onto the
// instance chosen by migrateDb(), which the hook awaits before anything else
// runs. Nothing is opened at import time, so `vite build` needs neither.
let active: Db | undefined;
let target: MigrationTarget | undefined;
let driver: 'd1' | 'libsql' | undefined;

export const db: Db = new Proxy({} as Db, {
	get(_, property) {
		if (!active) throw new Error('The database is not ready: migrateDb() runs in the hook first');
		const value = Reflect.get(active, property);
		return typeof value === 'function' ? value.bind(active) : value;
	}
});

let ready: Promise<void> | undefined;

async function open(d1: D1Database | undefined): Promise<void> {
	if (d1) {
		active = drizzleD1(d1, { schema }) as unknown as Db;
		target = d1Target(d1);
		driver = 'd1';
		return;
	}
	if (!env.DATABASE_URL) {
		throw new Error('No D1 binding "DB" and no DATABASE_URL: there is no database to open');
	}
	const { createClient } = await import('@libsql/client');
	const { drizzle } = await import('drizzle-orm/libsql');
	const client = createClient({ url: env.DATABASE_URL });
	active = drizzle(client, { schema }) as unknown as Db;
	target = libsqlTarget(client);
	driver = 'libsql';
}

/**
 * Opens the database on first use (once per isolate on a Worker), runs pending
 * migrations, and seeds the §7 gear inventory and Appendix B's starter cards if
 * their tables are empty.
 *
 * Foreign keys: D1 enforces them always. SQLite via libSQL defaults them off,
 * which would quietly disable the cascades on card_kinds/card_gear/card_tags
 * and the RESTRICT that stops gear being deleted while cards use it, so on
 * that path they go on after the migrations (which rebuild tables with them off).
 *
 * The migrations are bundled rather than read from drizzle/, because a Worker
 * has no filesystem; see ./migrations.ts.
 */
export function migrateDb(d1?: D1Database): Promise<void> {
	ready ??= (async () => {
		await open(d1);
		await applyMigrations(target!);
		if (driver === 'libsql') await target!.exec('PRAGMA foreign_keys = ON');
		const { seedGearIfEmpty, seedCardsIfEmpty } = await import('./seed');
		await seedGearIfEmpty();
		// Cards after gear: the starter set references gear slugs by id.
		await seedCardsIfEmpty();
	})();
	// A failed first run (a network blip on D1) must not poison the isolate: the
	// next request tries again instead of awaiting the same rejection forever.
	ready.catch(() => (ready = undefined));
	return ready;
}
