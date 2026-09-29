<script lang="ts">
	import { THEMES, themeLabel } from '$lib/theme';
	// Copied verbatim from brand/ at the repository root. Both inherit currentColor.
	import wordmark from '$lib/assets/brand/wordmark.svg?raw';
	import stamp from '$lib/assets/brand/stamp.svg?raw';

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
		<p class="note">
			Morning is the light theme and Night the dark one. Auto follows the device, and uses Morning
			when the device does not say.
		</p>
	</section>

	<section>
		<h2 class="label">Session</h2>
		<form method="POST" action="?/logout">
			<button type="submit" class="panel-button">Sign out</button>
		</form>
		<p class="note">Signing in again lasts a year. There is no password reset.</p>
	</section>

	<!--
		The one place the brand appears: the marks, and the product's accent as a
		lamp (ADR-004, ADR-027). Nowhere in the working screens.
	-->
	<section class="about" aria-labelledby="about-name">
		<div class="name">
			<span class="lamp" aria-hidden="true"></span>
			<h2 id="about-name" class="product">Sótão</h2>
			<span class="subtitle">studio cards</span>
		</div>
		<p class="note">
			Deals a card when you sit down in the studio and do not know where to start. Built for one
			attic and the gear in it. Not published.
		</p>
		<!-- The two marks are our own SVG files, bundled at build time: no input reaches them. -->
		<div class="marks">
			<!-- eslint-disable-next-line svelte/no-at-html-tags -->
			<span class="wordmark">{@html wordmark}</span>
			<!-- eslint-disable-next-line svelte/no-at-html-tags -->
			<span class="stamp">{@html stamp}</span>
		</div>
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

	.about {
		border-top: 1px solid var(--line);
		padding-top: 32px;
	}

	.name {
		display: flex;
		align-items: baseline;
		gap: 12px;
		flex-wrap: wrap;
	}

	/* The lamp: Ochre, the product's accent, as a status lamp on a panel. */
	.lamp {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--lamp);
		align-self: center;
	}

	.product {
		margin: 0;
		font-family: var(--font-display);
		font-variant-numeric: lining-nums;
		font-size: 30px;
		font-weight: 600;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		color: var(--text);
		line-height: 1;
	}

	.subtitle {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.29em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.marks {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 24px;
		color: var(--text);
		opacity: 0.75;
	}

	.marks :global(svg) {
		display: block;
		height: auto;
	}

	.wordmark :global(svg) {
		width: 200px;
	}

	.stamp :global(svg) {
		width: 40px;
	}

	@media (max-width: 720px) {
		.page {
			padding: 28px 16px;
		}
	}
</style>
