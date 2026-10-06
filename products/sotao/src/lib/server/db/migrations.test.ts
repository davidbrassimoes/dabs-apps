import { createClient } from '@libsql/client';
import { readMigrationFiles } from 'drizzle-orm/migrator';
import { describe, expect, it } from 'vitest';
import { applyMigrations, bundledMigrations, d1Target, libsqlTarget } from './migrations';

// The bundled migrator stands in for drizzle-orm's, which reads the disk. The
// two must agree exactly, or a database migrated by one looks out of date — or
// worse, up to date — to the other.

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

		expect(await applyMigrations(libsqlTarget(client))).toBe(bundledMigrations().length);
		expect(await applyMigrations(libsqlTarget(client))).toBe(0);

		const tables = await client.execute(
			"SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'cards'"
		);
		expect(tables.rows).toHaveLength(1);
		client.close();
	});
});

// D1 has no local fake in vitest, so this one is a thin D1-shaped wrapper over
// libSQL: the same prepare/bind/run/first/batch surface the adapter uses.
function fakeD1(client: ReturnType<typeof createClient>) {
	const calls: string[] = [];
	const statement = (sql: string, args: unknown[] = []) => ({
		sql,
		args,
		bind: (...values: unknown[]) => statement(sql, values),
		run: async () => {
			calls.push(sql);
			await client.execute({ sql, args: args as never });
		},
		first: async () => {
			const result = await client.execute({ sql, args: args as never });
			return result.rows[0] ?? null;
		}
	});
	return {
		calls,
		prepare: (sql: string) => statement(sql),
		batch: async (statements: ReturnType<typeof statement>[]) => {
			calls.push(`batch(${statements.length}): ${statements[0].sql}`);
			await client.batch(
				statements.map((s) => ({ sql: s.sql, args: s.args as never })),
				'write'
			);
		}
	};
}

describe('the D1 migration target', () => {
	it('applies once in one batch led by defer_foreign_keys, and is a no-op the second time', async () => {
		const client = createClient({ url: ':memory:' });
		const d1 = fakeD1(client);
		const target = d1Target(d1 as never);

		expect(await applyMigrations(target)).toBe(bundledMigrations().length);
		expect(d1.calls.filter((c) => c.startsWith('batch'))).toEqual([
			expect.stringContaining('PRAGMA defer_foreign_keys = true')
		]);
		expect(await applyMigrations(target)).toBe(0);

		const tables = await client.execute(
			"SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'cards'"
		);
		expect(tables.rows).toHaveLength(1);
		client.close();
	});
});
