<script lang="ts">
	import { THEMES, themeLabel } from '$lib/theme';

	let { data } = $props();
</script>

<svelte:head><title>Settings — Sótão Cards</title></svelte:head>

<div class="page">
	<h1 class="display">Settings</h1>

	<section>
		<h2 class="label">Theme</h2>
		<form method="POST" action="?/theme" class="choices">
			{#each THEMES as theme (theme)}
				<button
					type="submit"
					name="theme"
					value={theme}
					class="choice"
					class:current={data.theme === theme}
					aria-pressed={data.theme === theme}
				>
					{themeLabel(theme)}
				</button>
			{/each}
		</form>
		<p class="note">
			“System” follows the device. Dark is the default, and both themes are designed — neither is an
			afterthought.
		</p>
	</section>

	<section>
		<h2 class="label">Session</h2>
		<form method="POST" action="?/logout">
			<button type="submit" class="panel-button">Sign out</button>
		</form>
		<p class="note">Signing in again lasts a year. There is no password reset.</p>
	</section>
</div>

<style>
	.page {
		width: 100%;
		max-width: 620px;
		margin: 0 auto;
		box-sizing: border-box;
		padding: 44px 36px;
		display: flex;
		flex-direction: column;
		gap: 40px;
	}

	h1 {
		margin: 0;
		font-size: 30px;
	}

	section {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}

	h2 {
		margin: 0;
	}

	.choices {
		display: flex;
		gap: 3px;
		padding: 3px;
		border: 1px solid var(--line);
		border-radius: 3px;
		align-self: flex-start;
	}

	.choice {
		background: none;
		border: none;
		border-radius: 2px;
		color: var(--text-dim);
		font-family: var(--font-mono);
		font-size: 11px;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		padding: 10px 18px;
		min-height: 44px;
		cursor: pointer;
	}

	.choice.current {
		background: var(--text);
		color: var(--ground);
		font-weight: 600;
	}

	.note {
		margin: 0;
		font-size: 13px;
		line-height: 1.55;
		color: var(--text-dim);
		max-width: 52ch;
	}

	@media (max-width: 720px) {
		.page {
			padding: 28px 16px;
		}
	}
</style>
