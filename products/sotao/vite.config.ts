import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-cloudflare';
import { sveltekit } from '@sveltejs/kit/vite';

// Under Vitest there is no D1 binding, so the database module opens libSQL in
// memory. CI has no .env and needs no secrets to run the suite. Set on the
// process because that is where $env/dynamic/private reads from; vitest's own
// test.env does not reach it.
if (process.env.VITEST) process.env.DATABASE_URL = 'file::memory:';

export default defineConfig({
	// Honour a PORT handed to us, so several sessions can run a dev server
	// side by side instead of fighting over one number.
	server: { port: Number(process.env.PORT) || 5173 },
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			adapter: adapter(),
			typescript: {
				config: (config) => {
					config.include.push('../drizzle.config.ts');
				}
			}
		})
	],
	test: {
		expect: { requireAssertions: true },
		projects: [
			{
				extends: './vite.config.ts',
				test: {
					name: 'server',
					environment: 'node',
					include: ['src/**/*.{test,spec}.{js,ts}'],
					exclude: ['src/**/*.svelte.{test,spec}.{js,ts}']
				}
			}
		]
	}
});
