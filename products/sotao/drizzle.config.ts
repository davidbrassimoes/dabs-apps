import { defineConfig } from 'drizzle-kit';

// Only `drizzle-kit generate` is used: it writes the migration files from
// schema.ts. The app applies them itself (src/lib/server/db/migrations.ts), to
// D1 in production and dev and to libSQL in tests, so there is no database URL.
export default defineConfig({
	schema: './src/lib/server/db/schema.ts',
	dialect: 'sqlite',
	verbose: true,
	strict: true
});
