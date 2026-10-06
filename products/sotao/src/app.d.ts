import type { D1Database } from '@cloudflare/workers-types';
import type { Theme } from '$lib/theme';

// See https://svelte.dev/docs/kit/types#app.d.ts
declare global {
	namespace App {
		interface Platform {
			env: { DB: D1Database };
		}

		interface Locals {
			authenticated: boolean;
			theme: Theme;
		}
	}
}

export {};
