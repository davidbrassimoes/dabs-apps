<script lang="ts">
	import { THEMES, themeLabel } from '$lib/theme';

	let { data } = $props();
</script>

<svelte:head><title>Settings — Sótão</title></svelte:head>

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
		<p class="note">Auto follows the device, and uses Dark when the device does not say.</p>
	</section>

	<section>
		<h2 class="label">Session</h2>
		<form method="POST" action="?/logout">
			<button type="submit" class="panel-button">Sign out</button>
		</form>
		<p class="note">Signing in again lasts a year. There is no password reset.</p>
	</section>

	<!-- The product's name, and one lamp dot. No other branding. -->
	<section class="about" aria-labelledby="about-name">
		<div class="name">
			<span class="lamp" aria-hidden="true"></span>
			<h2 id="about-name" class="product">sótão</h2>
			<span class="subtitle">studio cards</span>
		</div>
		<p class="note">
			Deals a card when you sit down in the studio and do not know where to start. Built for one
			attic and the gear in it. Not published.
		</p>
	</section>
</div>

<style>
	.page {
		width: 100%;
		max-width: 620px;
		margin: 0 auto;
		box-sizing: border-box;
		padding: 16px 16px 32px;
		display: flex;
		flex-direction: column;
		gap: 32px;
	}

	h1 {
		margin: 0;
		font-size: 32px;
	}

	section {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	h2 {
		margin: 0;
	}

	.choices {
		display: flex;
		gap: 4px;
		padding: 4px;
		background: var(--panel);
		border-radius: 999px;
		align-self: flex-start;
	}

	.choice {
		background: none;
		border: none;
		border-radius: 999px;
		color: var(--text-dim);
		font-family: var(--font-sans);
		font-size: 14px;
		font-weight: 600;
		padding: 0 20px;
		min-height: 44px;
		cursor: pointer;
	}

	.choice.current {
		background: var(--text);
		color: var(--ground);
	}

	.note {
		margin: 0;
		font-size: 14px;
		line-height: 1.55;
		color: var(--text-dim);
		max-width: 52ch;
	}

	.panel-button {
		align-self: flex-start;
	}

	.about {
		border-top: 1px solid var(--line);
		padding-top: 28px;
	}

	.name {
		display: flex;
		align-items: center;
		gap: 12px;
		flex-wrap: wrap;
	}

	.lamp {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		background: var(--lamp);
	}

	.product {
		margin: 0;
		font-size: 30px;
		font-weight: 800;
		letter-spacing: -0.02em;
		color: var(--text);
		line-height: 1;
	}

	.subtitle {
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
	}
</style>
