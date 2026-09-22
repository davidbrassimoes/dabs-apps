<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';

	// SvelteKit's own default (a bare <h1>404</h1><p>…</p>, no theme at all)
	// rendered inside the app shell — jarring against the rest of the app's
	// equipment-panel character. This replaces it for any thrown error(),
	// most often a card or gear id that no longer exists.
	let is404 = $derived(page.status === 404);
</script>

<svelte:head><title>{page.status} — Sótão Cards</title></svelte:head>

<div class="page">
	<span class="label">{page.status}</span>
	<h1 class="display">{is404 ? 'Not found' : 'Something went wrong'}</h1>
	<p class="note">{page.error?.message ?? 'An unexpected error occurred.'}</p>
	<a class="panel-button" href={resolve('/draw?mode=single')}>Back to Draw</a>
</div>

<style>
	.page {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 14px;
		padding: 60px 36px;
		text-align: center;
	}

	h1 {
		margin: 0;
		font-size: 34px;
	}

	.note {
		margin: 0;
		font-size: 14px;
		line-height: 1.6;
		color: var(--text-dim);
		max-width: 46ch;
	}

	.panel-button {
		margin-top: 10px;
		text-decoration: none;
	}
</style>
