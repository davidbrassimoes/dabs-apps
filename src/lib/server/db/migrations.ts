import type { Client, InStatement } from '@libsql/client';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex, utf8ToBytes } from '@noble/hashes/utils.js';
import journal from '../../../../drizzle/meta/_journal.json';

// drizzle-orm's own migrator reads drizzle/ off the disk, and a Cloudflare
// Worker has no disk (ADR-017). So the migrations are bundled at build time
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

/** Applies every bundled migration newer than the last one recorded, in one batch. */
export async function applyMigrations(client: Client): Promise<number> {
	await client.execute(`
		CREATE TABLE IF NOT EXISTS ${MIGRATIONS_TABLE} (
			id SERIAL PRIMARY KEY,
			hash text NOT NULL,
			created_at numeric
		)
	`);

	const last = await client.execute(
		`SELECT created_at FROM ${MIGRATIONS_TABLE} ORDER BY created_at DESC LIMIT 1`
	);
	const lastWhen = last.rows.length > 0 ? Number(last.rows[0].created_at) : -Infinity;

	const pending = bundledMigrations().filter((m) => m.when > lastWhen);
	if (pending.length === 0) return 0;

	const batch: InStatement[] = [];
	for (const migration of pending) {
		for (const statement of migration.statements) batch.push(statement);
		batch.push({
			sql: `INSERT INTO ${MIGRATIONS_TABLE} ("hash", "created_at") VALUES (?, ?)`,
			args: [migration.hash, migration.when]
		});
	}
	// client.migrate runs the batch with foreign keys off, as drizzle's does:
	// drizzle-kit rebuilds tables, which the constraints would otherwise block.
	await client.migrate(batch);
	return pending.length;
}
