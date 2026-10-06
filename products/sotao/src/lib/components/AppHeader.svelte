<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { themeLabel, nextTheme, type Theme } from '$lib/theme';

	import type { Snippet } from 'svelte';

	type Props = { theme: Theme; rack: { inRack: number; owned: number }; children?: Snippet };
	let { theme, rack, children }: Props = $props();

	// On /gear the chip is plain text — it would only link to itself.
	let onGear = $derived(page.url.pathname === '/gear');

	// The mode toggle is a link pair rather than a control, so the phone can be
	// installed pointing straight at one mode (§9).
	let onDraw = $derived(page.url.pathname === '/draw');
	let drawMode = $derived(page.url.searchParams.get('mode') === 'spread' ? 'spread' : 'single');

	const links = [
		{ href: resolve('/draw'), label: 'Draw' },
		{ href: resolve('/cards'), label: 'Cards' },
		{ href: resolve('/gear'), label: 'Gear' },
		{ href: resolve('/import'), label: 'Import' }
	];

	function isActive(href: string): boolean {
		return page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}

	let following = $derived(nextTheme(theme));
</script>

<header>
	<span class="wordmark">sótão</span>

	<nav aria-label="Sections">
		{#each links as link (link.href)}
			<a
				href={link.href}
				class="nav-link"
				class:active={isActive(link.href)}
				aria-current={isActive(link.href) ? 'page' : undefined}>{link.label}</a
			>
		{/each}
	</nav>

	<div class="spacer"></div>

	{@render children?.()}

	{#if onDraw}
		<div class="modes">
			{#each ['single', 'spread'] as option (option)}
				<a
					href={resolve(`/draw?mode=${option}`)}
					class="mode"
					class:on={drawMode === option}
					aria-current={drawMode === option ? 'page' : undefined}
					>{option === 'single' ? 'One' : 'Three'}</a
				>
			{/each}
		</div>
	{:else if onGear}
		<span class="rack rack-plain">{rack.inRack} of {rack.owned} in the rack</span>
	{:else}
		<a href={resolve('/gear')} class="rack">{rack.inRack} of {rack.owned} in the rack</a>
	{/if}

	<a href={resolve('/settings')} class="icon-button" aria-label="Settings" title="Settings">
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path
				d="M3 6h18M3 12h18M3 18h18"
				stroke="currentColor"
				stroke-width="2"
				stroke-linecap="round"
			/>
			<circle cx="8" cy="6" r="2.2" fill="var(--ground)" stroke="currentColor" stroke-width="2" />
			<circle cx="16" cy="12" r="2.2" fill="var(--ground)" stroke="currentColor" stroke-width="2" />
			<circle cx="10" cy="18" r="2.2" fill="var(--ground)" stroke="currentColor" stroke-width="2" />
		</svg>
	</a>

	<form method="POST" action="/api/theme" class="theme-form">
		<input type="hidden" name="theme" value={following} />
		<input type="hidden" name="from" value={page.url.pathname + page.url.search} />
		<button
			type="submit"
			class="icon-button"
			aria-label="Switch theme — currently {themeLabel(theme)}"
			title="Theme: {themeLabel(theme)}"
		>
			{#if theme === 'dark'}
				<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<path
						d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
						stroke="currentColor"
						stroke-width="2"
						stroke-linejoin="round"
					/>
				</svg>
			{:else if theme === 'light'}
				<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<circle cx="12" cy="12" r="4.5" stroke="currentColor" stroke-width="2" />
					<path
						d="M12 2v2.5M12 19.5V22M22 12h-2.5M4.5 12H2M19 5l-1.8 1.8M6.8 17.2L5 19M19 19l-1.8-1.8M6.8 6.8L5 5"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
					/>
				</svg>
			{:else}
				<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<rect
						x="3"
						y="4.5"
						width="18"
						height="12.5"
						rx="2"
						stroke="currentColor"
						stroke-width="2"
					/>
					<path d="M8 21h8" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
				</svg>
			{/if}
		</button>
	</form>
</header>

<style>
	header {
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 12px 16px 6px;
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.wordmark {
		font-weight: 800;
		font-size: 24px;
		letter-spacing: -0.02em;
		color: var(--text);
		white-space: nowrap;
	}

	.spacer {
		flex-grow: 1;
	}

	/* A phone: the sections are a tab bar at the bottom. */
	nav {
		position: fixed;
		z-index: 10;
		left: 0;
		right: 0;
		bottom: 0;
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		padding: 0 8px calc(8px + env(safe-area-inset-bottom));
		background: var(--ground);
		border-top: 1px solid var(--line);
	}

	.nav-link {
		display: flex;
		align-items: center;
		justify-content: center;
		min-height: 48px;
		font-size: 14px;
		font-weight: 600;
		color: var(--text-dim);
		text-decoration: none;
	}

	.nav-link.active {
		font-weight: 800;
		color: var(--text);
	}

	.modes {
		display: flex;
		gap: 4px;
		padding: 4px;
		background: var(--panel);
		border-radius: 999px;
	}

	.mode {
		display: flex;
		align-items: center;
		min-height: 44px;
		box-sizing: border-box;
		padding: 0 16px;
		border-radius: 999px;
		font-size: 14px;
		font-weight: 600;
		color: var(--text-dim);
		text-decoration: none;
	}

	.mode.on {
		background: var(--text);
		color: var(--ground);
	}

	.rack {
		display: none;
		align-items: center;
		min-height: 44px;
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
		text-decoration: none;
		white-space: nowrap;
		font-variant-numeric: tabular-nums;
	}

	.rack-plain {
		color: var(--text);
		font-weight: 700;
	}

	.theme-form {
		display: none;
	}

	.icon-button {
		display: flex;
		align-items: center;
		justify-content: center;
		box-sizing: border-box;
		width: 44px;
		height: 44px;
		border: 0;
		border-radius: 999px;
		background: none;
		color: var(--text-dim);
		cursor: pointer;
	}

	.icon-button:hover {
		color: var(--text);
		background: var(--panel);
	}

	/* Wider than a phone: the sections move up into the header. */
	@media (min-width: 721px) {
		header {
			padding: 12px 36px;
			gap: 12px;
		}

		.wordmark {
			margin-right: 20px;
		}

		nav {
			position: static;
			display: flex;
			gap: 4px;
			padding: 0;
			background: none;
			border: 0;
		}

		.nav-link {
			min-height: 44px;
			padding: 0 14px;
			border-radius: 999px;
		}

		.nav-link:hover {
			color: var(--text);
		}

		.nav-link.active {
			background: var(--panel);
		}

		.rack {
			display: flex;
		}

		.theme-form {
			display: flex;
		}
	}
</style>
