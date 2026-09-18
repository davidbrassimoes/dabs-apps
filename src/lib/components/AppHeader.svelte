<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { themeLabel, nextTheme, type Theme } from '$lib/theme';

	import type { Snippet } from 'svelte';

	type Props = { theme: Theme; children?: Snippet };
	let { theme, children }: Props = $props();

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
	<span class="wordmark">Sótão Cards</span>

	<nav>
		{#each links as link (link.href)}
			<a href={link.href} class="nav-link" class:active={isActive(link.href)}>{link.label}</a>
		{/each}
	</nav>

	<div class="spacer"></div>

	{@render children?.()}

	<!--
		The mockups draw no settings control, but §10 asks for logout to live in a
		settings menu rather than the header. This cog is the smallest thing that
		gives it a home without disturbing the drawn layout.
	-->
	<a href={resolve('/settings')} class="theme-button" aria-label="Settings" title="Settings">
		<svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
			<path
				d="M0.8 3.2h12.4M0.8 7h12.4M0.8 10.8h12.4"
				stroke="currentColor"
				stroke-width="1.2"
				stroke-linecap="round"
			/>
			<circle
				cx="4.4"
				cy="3.2"
				r="1.5"
				fill="var(--ground)"
				stroke="currentColor"
				stroke-width="1.2"
			/>
			<circle
				cx="9.6"
				cy="7"
				r="1.5"
				fill="var(--ground)"
				stroke="currentColor"
				stroke-width="1.2"
			/>
			<circle
				cx="5.6"
				cy="10.8"
				r="1.5"
				fill="var(--ground)"
				stroke="currentColor"
				stroke-width="1.2"
			/>
		</svg>
	</a>

	<form method="POST" action="/api/theme" class="theme-form">
		<input type="hidden" name="theme" value={following} />
		<input type="hidden" name="from" value={page.url.pathname + page.url.search} />
		<button
			type="submit"
			class="theme-button"
			aria-label="Switch theme — currently {themeLabel(theme)}"
			title="Theme: {themeLabel(theme)}"
		>
			{#if theme === 'dark'}
				<svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
					<circle cx="7" cy="7" r="3" stroke="currentColor" stroke-width="1.2" />
					<path
						d="M7 0.6v1.7M7 11.7v1.7M13.4 7h-1.7M2.3 7H0.6M11.5 2.5l-1.2 1.2M3.7 10.3l-1.2 1.2M11.5 11.5l-1.2-1.2M3.7 3.7L2.5 2.5"
						stroke="currentColor"
						stroke-width="1.2"
					/>
				</svg>
			{:else if theme === 'light'}
				<svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
					<path
						d="M12.2 8.6A5.6 5.6 0 0 1 5.4 1.8a5.6 5.6 0 1 0 6.8 6.8z"
						stroke="currentColor"
						stroke-width="1.2"
						stroke-linejoin="round"
					/>
				</svg>
			{:else}
				<svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
					<rect
						x="0.9"
						y="2.2"
						width="12.2"
						height="8.4"
						rx="1"
						stroke="currentColor"
						stroke-width="1.2"
					/>
					<path d="M4.6 12.6h4.8" stroke="currentColor" stroke-width="1.2" />
				</svg>
			{/if}
		</button>
	</form>
</header>

<style>
	header {
		height: 58px;
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 0 36px;
		border-bottom: 1px solid var(--line);
		display: flex;
		align-items: center;
		gap: 26px;
	}

	.wordmark {
		font-family: var(--font-mono);
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.22em;
		text-transform: uppercase;
		color: var(--text);
		white-space: nowrap;
	}

	nav {
		display: flex;
		gap: 20px;
		align-items: center;
	}

	.nav-link {
		font-family: var(--font-mono);
		font-size: 11px;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--text-dim);
		text-decoration: none;
		padding-bottom: 3px;
		border-bottom: 2px solid transparent;
	}

	.nav-link:hover {
		color: var(--text);
	}

	.nav-link.active {
		color: var(--text);
		border-bottom-color: var(--kind-treatment);
	}

	.spacer {
		flex-grow: 1;
	}

	.theme-form {
		display: flex;
	}

	.theme-button {
		background: none;
		border: 1px solid var(--line);
		border-radius: 3px;
		color: var(--text-dim);
		padding: 9px 10px;
		line-height: 0;
		cursor: pointer;
	}

	.theme-button:hover {
		color: var(--text);
	}

	@media (max-width: 720px) {
		header {
			height: auto;
			padding: 10px 16px;
			flex-wrap: wrap;
			gap: 12px 16px;
		}

		nav {
			gap: 16px;
			order: 3;
			width: 100%;
		}

		.nav-link {
			min-height: 44px;
			display: flex;
			align-items: center;
		}

		.theme-button {
			min-height: 44px;
			min-width: 44px;
			display: flex;
			align-items: center;
			justify-content: center;
		}
	}
</style>
