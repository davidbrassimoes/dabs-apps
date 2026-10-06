<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { untrack } from 'svelte';
	import { SvelteURLSearchParams } from 'svelte/reactivity';
	import { ALL_KINDS } from '$lib/kinds';
	import KindTag from '$lib/components/KindTag.svelte';
	import FavouriteStar from '$lib/components/FavouriteStar.svelte';
	import type { Kind } from '$lib/vocab';

	let { data } = $props();

	// Seeded once on purpose: after this, the box is the owner's to type in and
	// the query string follows it, not the other way round.
	let search = $state(untrack(() => data.filters.search));
	let debounce: ReturnType<typeof setTimeout> | undefined;

	// Search is substring, case-insensitive, debounced 150 ms (§9).
	function onSearch(value: string) {
		search = value;
		clearTimeout(debounce);
		debounce = setTimeout(() => apply({ q: value || null }), 150);
	}

	/** Rewrites the query string, which is where every filter lives. */
	function apply(changes: Record<string, string | string[] | null>) {
		const params = new SvelteURLSearchParams(page.url.search);
		for (const [key, value] of Object.entries(changes)) {
			params.delete(key);
			if (Array.isArray(value)) for (const v of value) params.append(key, v);
			else if (value !== null) params.set(key, value);
		}
		goto(resolve(`/cards?${params.toString()}`), {
			keepFocus: true,
			noScroll: true,
			replaceState: true
		});
	}

	function toggleKind(kind: Kind) {
		const next = data.filters.kinds.includes(kind)
			? data.filters.kinds.filter((k) => k !== kind)
			: [...data.filters.kinds, kind];
		apply({ kind: next });
	}

	let statusLabel = $derived(
		data.filters.status === 'active'
			? 'Active only'
			: data.filters.status === 'archived'
				? 'Archived only'
				: 'Any status'
	);

	function cycleStatus() {
		apply({
			status:
				data.filters.status === 'active'
					? 'archived'
					: data.filters.status === 'archived'
						? 'any'
						: 'active'
		});
	}

	function cycleAuthor() {
		apply({
			author:
				data.filters.author === null ? 'david' : data.filters.author === 'david' ? 'claude' : null
		});
	}

	let authorLabel = $derived(
		data.filters.author === null ? 'Author: any' : `Author: ${data.filters.author}`
	);
</script>

<svelte:head><title>Cards — Sótão</title></svelte:head>

<div class="screen">
	<header class="top">
		<h1 class="display">
			Cards
			<span class="count">{data.items.length} {data.items.length === 1 ? 'match' : 'matches'}</span>
		</h1>
		<div class="actions">
			<a class="panel-button" href={resolve('/api/export.yaml')} download>Export</a>
			<a class="new" href={resolve('/cards/new')} aria-label="New card" title="New card">
				<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<path
						d="M12 5v14M5 12h14"
						stroke="currentColor"
						stroke-width="2.4"
						stroke-linecap="round"
					/>
				</svg>
			</a>
		</div>
	</header>

	<div class="search">
		<label class="sr-only" for="card-search">Search</label>
		<input
			id="card-search"
			type="search"
			value={search}
			oninput={(e) => onSearch(e.currentTarget.value)}
			placeholder="Search card text"
		/>
	</div>

	<div class="chips">
		<button
			type="button"
			class="chip"
			class:on={data.filters.kinds.length === 0}
			aria-pressed={data.filters.kinds.length === 0}
			onclick={() => apply({ kind: [] })}>All kinds</button
		>
		{#each ALL_KINDS as kind (kind)}
			<button
				type="button"
				class="chip kind"
				class:on={data.filters.kinds.includes(kind)}
				aria-pressed={data.filters.kinds.includes(kind)}
				style="--fill: var(--card-{kind}); --on: var(--card-{kind}-ink); --dot: var(--kind-{kind})"
				onclick={() => toggleKind(kind)}
			>
				<span class="dot"></span>
				{kind}
				<span class="n">{data.counts[kind] ?? 0}</span>
			</button>
		{/each}

		<button type="button" class="chip" onclick={cycleAuthor}>{authorLabel}</button>
		<button type="button" class="chip" class:on={data.filters.status !== null} onclick={cycleStatus}
			>{statusLabel}</button
		>
		<select
			class="chip select"
			value={data.filters.gear ?? ''}
			onchange={(e) => apply({ gear: e.currentTarget.value || null })}
			aria-label="Filter by gear"
		>
			<option value="">Gear: any</option>
			{#each data.gear as item (item.id)}
				<option value={item.id}>{item.id}</option>
			{/each}
		</select>
		<button
			type="button"
			class="chip fav"
			class:on={data.filters.favouritesOnly}
			aria-pressed={data.filters.favouritesOnly}
			onclick={() => apply({ fav: data.filters.favouritesOnly ? null : '1' })}
		>
			<FavouriteStar filled={data.filters.favouritesOnly} size={16} />
			Favourites
		</button>
	</div>

	<ul class="rows">
		{#each data.items as card (card.id)}
			<li>
				<a
					class="row"
					class:dim={card.hasUnownedGear}
					href={resolve('/cards/[id]', { id: card.id })}
				>
					<span
						class="swatch"
						style="--fill: var(--card-{card.kinds[0] ?? 'idea'}); --on: var(--card-{card.kinds[0] ??
							'idea'}-ink)"
						aria-hidden="true">{(card.kinds[0] ?? 'idea').charAt(0)}</span
					>
					<span class="body">
						<span class="text">{card.text}</span>
						<span class="meta line">
							<span class="kinds">
								{#each card.kinds as kind (kind)}<KindTag {kind} />{/each}
							</span>
							<span class="gear">{card.gear.length ? card.gear.join(' · ') : '—'}</span>
							{#if card.tags.length}<span class="tags">#{card.tags.join(' #')}</span>{/if}
							<span class="grow"></span>
							{#if card.favorite}<span class="fav-mark" role="img" aria-label="Favourite"
									><FavouriteStar filled size={14} /></span
								>{/if}
							<span>{card.drawCount > 0 ? `${card.drawCount}×` : '—'}</span>
							<span class="by" class:claude={card.author === 'claude'}>{card.author}</span>
						</span>
					</span>
				</a>
			</li>
		{:else}
			<li class="empty">
				{#if data.filters.search || data.filters.kinds.length || data.filters.gear || data.filters.favouritesOnly || data.filters.author}
					Nothing matches those filters.
				{:else}
					No cards yet. Write one, or paste a batch into Import.
				{/if}
			</li>
		{/each}
	</ul>

	<p class="meta foot">Tap a card to edit · Sorted by least recently drawn</p>
</div>

<style>
	.screen {
		flex-grow: 1;
		width: 100%;
		max-width: 960px;
		margin: 0 auto;
		box-sizing: border-box;
		padding: 0 16px 24px;
		display: flex;
		flex-direction: column;
	}

	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 10px 0 6px;
	}

	h1 {
		margin: 0;
		font-size: 32px;
	}

	.count {
		font-family: var(--font-sans);
		font-weight: 400;
		font-size: 18px;
		letter-spacing: 0;
		color: var(--text-dim);
	}

	.actions {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.new {
		width: 52px;
		height: 52px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 18px;
		background: var(--text);
		color: var(--ground);
	}

	.sr-only {
		position: absolute;
		left: -9999px;
	}

	input[type='search'] {
		width: 100%;
		box-sizing: border-box;
		height: 48px;
		border: 0;
		border-radius: 16px;
		background: var(--panel);
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 16px;
		padding: 0 16px;
	}

	input[type='search']::placeholder {
		color: var(--text-dim);
	}

	.search {
		padding-top: 8px;
	}

	.chips {
		display: flex;
		gap: 6px;
		padding: 12px 0;
		overflow-x: auto;
		flex-wrap: nowrap;
	}

	@media (min-width: 721px) {
		.chips {
			flex-wrap: wrap;
			overflow: visible;
		}
	}

	.chip {
		flex: none;
		display: flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
		box-sizing: border-box;
		padding: 0 14px;
		border: 0;
		border-radius: 999px;
		background: var(--panel);
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 14px;
		font-weight: 600;
		text-transform: capitalize;
		cursor: pointer;
	}

	.chip.on {
		background: var(--text);
		color: var(--ground);
	}

	.chip.kind.on {
		background: var(--fill);
		color: var(--on);
	}

	.dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		background: var(--dot);
	}

	.chip.kind.on .dot {
		background: var(--on);
	}

	.n {
		font-family: var(--font-mono);
		font-weight: 400;
		font-variant-numeric: tabular-nums;
	}

	.rows {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.row {
		display: flex;
		gap: 12px;
		align-items: stretch;
		min-height: 44px;
		background: var(--panel);
		border-radius: 16px;
		padding: 10px 12px 10px 10px;
		text-decoration: none;
		color: var(--text);
	}

	.row:hover {
		background: var(--ground);
	}

	.swatch {
		flex: none;
		width: 34px;
		border-radius: 10px;
		background: var(--fill);
		color: var(--on);
		display: flex;
		align-items: center;
		justify-content: center;
		font-family: var(--font-mono);
		font-weight: 700;
		font-size: 14px;
		text-transform: uppercase;
	}

	.body {
		display: flex;
		flex-direction: column;
		gap: 3px;
		min-width: 0;
		flex-grow: 1;
	}

	.text {
		font-weight: 600;
		font-size: 16px;
		line-height: 21px;
	}

	/* Cards referencing gear that is not owned render dimmed (§9). */
	.row.dim .text {
		color: var(--text-dim);
	}

	.line {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 2px 12px;
		font-size: 12px;
	}

	.kinds {
		display: flex;
		gap: 8px;
	}

	.kinds :global(.tag) {
		font-size: 11px;
		letter-spacing: 0.12em;
	}

	.tags {
		color: var(--text-dim);
	}

	.grow {
		flex-grow: 1;
	}

	.fav-mark {
		display: flex;
		color: var(--lamp);
	}

	.by.claude {
		color: var(--text);
	}

	.empty {
		padding: 48px 0;
		font-size: 16px;
		color: var(--text-dim);
	}

	.foot {
		margin: 16px 0 0;
	}
</style>
