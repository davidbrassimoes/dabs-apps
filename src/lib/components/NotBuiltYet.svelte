<script lang="ts">
	// A stand-in for screens later milestones build. It exists so the header nav
	// resolves during M0 rather than 404ing, and it is deleted as each screen
	// lands. It talks to nothing.
	type Props = { title: string; milestone: string; what: string };
	let { title, milestone, what }: Props = $props();
</script>

<svelte:head><title>{title} — Sótão Cards</title></svelte:head>

<div class="page">
	<span class="label">{milestone}</span>
	<h1 class="display">{title}</h1>
	<p class="note">{what}</p>
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
</style>
