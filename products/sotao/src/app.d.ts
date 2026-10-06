import type { Theme } from '$lib/theme';

// See https://svelte.dev/docs/kit/types#app.d.ts
declare global {
	namespace App {
		interface Locals {
			authenticated: boolean;
			theme: Theme;
		}
	}
}

export {};
