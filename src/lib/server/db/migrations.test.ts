import { createClient } from '@libsql/client';
import { readMigrationFiles } from 'drizzle-orm/migrator';
import { describe, expect, it } from 'vitest';
import { applyMigrations, bundledMigrations } from './migrations';

// The bundled migrator stands in for drizzle-orm's, which reads the disk. The
// two must agree exactly, or a database migrated by one looks out of date — or
// worse, up to date — to the other (ADR-027).

describe('the bundled migrations', () => {
	it('are what drizzle-orm reads from drizzle/: same order, SQL, timestamps and hashes', () => {
		const theirs = readMigrationFiles({ migrationsFolder: './drizzle' });
		const ours = bundledMigrations();

		expect(ours.map((m) => m.when)).toEqual(theirs.map((m) => m.folderMillis));
		expect(ours.map((m) => m.hash)).toEqual(theirs.map((m) => m.hash));
		expect(ours.map((m) => m.statements)).toEqual(theirs.map((m) => m.sql));
	});

	it('apply to an empty database once, and are a no-op the second time', async () => {
		const client = createClient({ url: ':memory:' });

		expect(await applyMigrations(client)).toBe(bundledMigrations().length);
		expect(await applyMigrations(client)).toBe(0);

		const tables = await client.execute(
			"SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'cards'"
		);
		expect(tables.rows).toHaveLength(1);
		client.close();
	});
});
