import type { Client } from '@libsql/client';
import type { D1Database } from '@cloudflare/workers-types';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex, utf8ToBytes } from '@noble/hashes/utils.js';
import journal from '../../../../drizzle/meta/_journal.json';

// drizzle-orm's own migrator reads drizzle/ off the disk, and a Cloudflare
// Worker has no disk. So the migrations are bundled at build time
// instead, and applied here the way drizzle-orm/libsql/migrator applies them —
// the same __drizzle_migrations table, the same hash (SHA-256 of the file), the
// same timestamp (the journal's `when`), the same statement breakpoints. A
// database migrated by either one is up to date for the other, and for
// `drizzle-kit migrate`.
//
// drizzle-kit still writes the files; nothing about generating a migration
// changes. If drizzle-orm changes its table or its hash, this is what to update.

const MIGRATIONS_TABLE = '__drizzle_migrations';
const BREAKPOINT = '--> statement-breakpoint';

const files = import.meta.glob<string>('/drizzle/*.sql', {
	query: '?raw',
	import: 'default',
	eager: true
});

type Migration = { tag: string; statements: string[]; when: number; hash: string };

/** The journal's entries with their SQL, in order. A missing file is a broken build, not a skip. */
export function bundledMigrations(): Migration[] {
	return journal.entries.map((entry) => {
		const sql = files[`/drizzle/${entry.tag}.sql`];
		if (sql === undefined)
			throw new Error(`drizzle/${entry.tag}.sql is in the journal but not bundled`);
		return {
			tag: entry.tag,
			statements: sql.split(BREAKPOINT),
			when: entry.when,
			hash: bytesToHex(sha256(utf8ToBytes(sql)))
		};
	});
}

type Statement = { sql: string; args?: (string | number)[] };

/**
 * What the migrator needs from a database, so one routine serves both
 * drivers: libSQL (tests, anything with a file) and D1 (the Worker).
 */
export interface MigrationTarget {
	/** One statement, no result wanted. */
	exec(sql: string): Promise<void>;
	/** The newest created_at in the migrations table, or -Infinity when empty. */
	lastWhen(): Promise<number>;
	/** All of these or none of them. */
	batch(statements: Statement[]): Promise<void>;
}

export function libsqlTarget(client: Client): MigrationTarget {
	return {
		async exec(sql) {
			await client.execute(sql);
		},
		async lastWhen() {
			const last = await client.execute(
				`SELECT created_at FROM ${MIGRATIONS_TABLE} ORDER BY created_at DESC LIMIT 1`
			);
			return last.rows.length > 0 ? Number(last.rows[0].created_at) : -Infinity;
		},
		// client.migrate runs the batch with foreign keys off, as drizzle's does:
		// drizzle-kit rebuilds tables, which the constraints would otherwise block.
		async batch(statements) {
			await client.migrate(statements.map((s) => (s.args ? { sql: s.sql, args: s.args } : s.sql)));
		}
	};
}

export function d1Target(d1: D1Database): MigrationTarget {
	return {
		async exec(sql) {
			await d1.prepare(sql).run();
		},
		async lastWhen() {
			const row = await d1
				.prepare(`SELECT created_at FROM ${MIGRATIONS_TABLE} ORDER BY created_at DESC LIMIT 1`)
				.first<{ created_at: number | string }>();
			return row ? Number(row.created_at) : -Infinity;
		},
		// D1 enforces foreign keys always and has no PRAGMA foreign_keys. Deferring
		// them to the end of the batch lets a table rebuild pass; the batch is atomic.
		async batch(statements) {
			const prepared = statements
				.filter((s) => s.sql.trim() !== '')
				.map((s) => (s.args ? d1.prepare(s.sql).bind(...s.args) : d1.prepare(s.sql)));
			await d1.batch([d1.prepare('PRAGMA defer_foreign_keys = true'), ...prepared]);
		}
	};
}

/** Applies every bundled migration newer than the last one recorded, in one batch. */
export async function applyMigrations(target: MigrationTarget): Promise<number> {
	await target.exec(`
		CREATE TABLE IF NOT EXISTS ${MIGRATIONS_TABLE} (
			id SERIAL PRIMARY KEY,
			hash text NOT NULL,
			created_at numeric
		)
	`);

	const lastWhen = await target.lastWhen();
	const pending = bundledMigrations().filter((m) => m.when > lastWhen);
	if (pending.length === 0) return 0;

	const batch: Statement[] = [];
	for (const migration of pending) {
		for (const statement of migration.statements) batch.push({ sql: statement });
		batch.push({
			sql: `INSERT INTO ${MIGRATIONS_TABLE} ("hash", "created_at") VALUES (?, ?)`,
			args: [migration.hash, migration.when]
		});
	}
	await target.batch(batch);
	return pending.length;
}
