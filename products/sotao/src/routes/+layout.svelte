<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	// The two faces, bundled from @fontsource and served from here, so nothing is
	// fetched from a third party. Both SIL OFL 1.1.
	import '@fontsource/bricolage-grotesque/400.css';
	import '@fontsource/bricolage-grotesque/600.css';
	import '@fontsource/bricolage-grotesque/700.css';
	import '@fontsource/bricolage-grotesque/800.css';
	import '@fontsource/courier-prime/400.css';
	import '@fontsource/courier-prime/700.css';
	import './layout.css';
	import AppHeader from '$lib/components/AppHeader.svelte';
	import { page } from '$app/state';

	let { data, children } = $props();

	// /login stands on its own: no nav, no theme switch, nothing to navigate to.
	let chromeless = $derived(page.url.pathname === '/login');

	// Precaches the shell so a launch from the home screen works with the
	// network off (§11). See src/service-worker.ts for what it caches.
	onMount(() => {
		if (browser && 'serviceWorker' in navigator) {
			navigator.serviceWorker.register('/service-worker.js', {
				type: import.meta.env.DEV ? 'module' : 'classic'
			});
		}
	});
</script>

<svelte:head>
	<title>sótão</title>
</svelte:head>

{#if chromeless}
	{@render children()}
{:else}
	<div class="shell">
		<AppHeader theme={data.theme} rack={data.rack} />
		<main>
			{@render children()}
		</main>
	</div>
{/if}

<style>
	.shell {
		min-height: 100vh;
		min-height: 100dvh;
		display: flex;
		flex-direction: column;
		background: var(--ground);
	}

	main {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	/* The tab bar is fixed to the bottom on a phone; leave room for it. */
	@media (max-width: 720px) {
		main {
			padding-bottom: calc(64px + env(safe-area-inset-bottom));
		}
	}
</style>
