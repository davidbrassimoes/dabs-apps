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

<svelte:head><title>Cards — Sótão Cards</title></svelte:head>

<div class="screen">
	<div class="filters">
		<div class="line">
			<label class="label" for="card-search">Search</label>
			<input
				id="card-search"
				type="search"
				value={search}
				oninput={(e) => onSearch(e.currentTarget.value)}
				placeholder="card text…"
			/>
			<span class="meta">{data.items.length} {data.items.length === 1 ? 'match' : 'matches'}</span>
			<div class="spacer"></div>
			<!-- The export endpoint lands in M4; the button is drawn but inert until then. -->
			<button type="button" class="panel-button" disabled title="Export arrives with import, in M4">
				Export
			</button>
			<a class="primary" href={resolve('/cards/new')}>New card</a>
		</div>

		<div class="chips">
			<button
				type="button"
				class="chip"
				class:on={data.filters.kinds.length === 0}
				onclick={() => apply({ kind: [] })}>All kinds</button
			>
			{#each ALL_KINDS as kind (kind)}
				<button
					type="button"
					class="chip kind"
					class:on={data.filters.kinds.includes(kind)}
					style="--chip-ink: var(--kind-{kind})"
					onclick={() => toggleKind(kind)}
				>
					{kind}
					{data.counts[kind] ?? 0}
				</button>
			{/each}

			<span class="divider"></span>

			<button type="button" class="chip" onclick={cycleAuthor}>{authorLabel}</button>
			<button
				type="button"
				class="chip status"
				class:on={data.filters.status !== null}
				onclick={cycleStatus}>{statusLabel}</button
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
				onclick={() => apply({ fav: data.filters.favouritesOnly ? null : '1' })}
			>
				<FavouriteStar filled={data.filters.favouritesOnly} size={11} />
				Favourites
			</button>
		</div>
	</div>

	<div class="head">
		<span class="col-card">Card</span>
		<span class="col-kinds">Kinds</span>
		<span class="col-gear">Gear</span>
		<span class="col-tags">Tags</span>
		<span class="col-fav">Fav</span>
		<span class="col-drawn">Drawn</span>
		<span class="col-by">By</span>
	</div>

	<div class="rows">
		{#each data.items as card (card.id)}
			<a class="row" class:dim={card.hasUnownedGear} href={resolve('/cards/[id]', { id: card.id })}>
				<span class="col-card">{card.text}</span>
				<span class="col-kinds">
					{#each card.kinds as kind (kind)}<KindTag {kind} />{/each}
				</span>
				<span class="col-gear">{card.gear.length ? card.gear.join(' · ') : '—'}</span>
				<span class="col-tags">{card.tags.length ? card.tags.join(', ') : ''}</span>
				<span class="col-fav"><FavouriteStar filled={card.favorite} /></span>
				<span class="col-drawn">{card.drawCount > 0 ? `${card.drawCount}×` : '—'}</span>
				<span class="col-by" class:claude={card.author === 'claude'}>{card.author}</span>
			</a>
		{:else}
			<p class="empty">
				{#if data.filters.search || data.filters.kinds.length || data.filters.gear || data.filters.favouritesOnly || data.filters.author}
					Nothing matches those filters.
				{:else}
					No cards yet. Write one, or paste a batch into Import.
				{/if}
			</p>
		{/each}
	</div>

	<div class="rail">
		<span class="meta">Click a row to edit</span>
		<div class="spacer"></div>
		<span class="meta strong">Sorted by least recently drawn</span>
	</div>
</div>

<style>
	.screen {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	.filters {
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 20px 36px 16px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		border-bottom: 1px solid var(--line-soft);
	}

	.line {
		display: flex;
		align-items: center;
		gap: 12px;
	}

	input[type='search'] {
		width: 320px;
		box-sizing: border-box;
		background: var(--panel);
		border: 1px solid var(--line);
		border-radius: 2px;
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 13px;
		padding: 10px 12px;
		min-height: 40px;
	}

	.spacer {
		flex-grow: 1;
	}

	.primary {
		background: var(--text);
		border: none;
		border-radius: 2px;
		color: var(--ground);
		font-family: var(--font-mono);
		font-size: 10px;
		font-weight: 600;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		padding: 12px 17px;
		text-decoration: none;
	}

	.chips {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}

	.chip {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--text-dim);
		background: transparent;
		border: 1px solid var(--line);
		border-radius: 2px;
		padding: 6px 10px;
		cursor: pointer;
		min-height: 28px;
	}

	.chip.kind {
		color: var(--chip-ink);
	}

	.chip.on {
		color: var(--ground);
		background: var(--text);
		border-color: var(--text);
		font-weight: 600;
	}

	.chip.kind.on {
		background: var(--chip-ink);
		border-color: var(--chip-ink);
		color: var(--ground);
	}

	.chip.status.on {
		background: var(--kind-source);
		border-color: var(--kind-source);
		color: var(--ground);
	}

	.chip.fav {
		display: flex;
		align-items: center;
		gap: 6px;
	}

	.chip.select {
		font-family: var(--font-mono);
	}

	.divider {
		width: 1px;
		height: 22px;
		background: var(--line);
		display: block;
		margin: 0 6px;
	}

	.head,
	.row {
		display: flex;
		align-items: center;
		gap: 16px;
		box-sizing: border-box;
		padding: 0 36px;
	}

	.head {
		flex-shrink: 0;
		height: 34px;
		background: var(--rail);
		border-bottom: 1px solid var(--line-soft);
		font-family: var(--font-mono);
		font-size: 9px;
		font-weight: 600;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.rows {
		flex-grow: 1;
		overflow-y: auto;
		min-height: 0;
	}

	.row {
		height: 54px;
		border-bottom: 1px solid var(--row-line);
		text-decoration: none;
		color: inherit;
	}

	.row:hover {
		background: var(--panel);
	}

	.col-card {
		width: 452px;
		font-size: 13.5px;
		line-height: 1.35;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	/* Cards referencing gear that is not owned render dimmed (§9). */
	.row.dim .col-card {
		color: var(--text-muted);
	}

	.col-kinds {
		width: 152px;
		display: flex;
		gap: 6px;
		overflow: hidden;
	}

	.col-gear {
		width: 226px;
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.04em;
		color: var(--text-dim);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.col-tags {
		width: 110px;
		font-family: var(--font-mono);
		font-size: 10px;
		color: var(--kind-source);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.col-fav {
		width: 26px;
		line-height: 0;
	}

	.col-drawn {
		width: 58px;
		font-family: var(--font-mono);
		font-size: 11px;
		color: var(--text-dim);
		text-align: right;
		font-variant-numeric: tabular-nums;
	}

	.col-by {
		flex-grow: 1;
		font-family: var(--font-mono);
		font-size: 9.5px;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--kind-source);
		text-align: right;
	}

	.col-by.claude {
		color: var(--kind-technique);
	}

	.empty {
		margin: 0;
		padding: 48px 36px;
		font-size: 14px;
		color: var(--text-dim);
	}

	.rail {
		flex-shrink: 0;
		box-sizing: border-box;
		height: 54px;
		padding: 0 36px;
		background: var(--rail);
		border-top: 1px solid var(--line);
		display: flex;
		align-items: center;
		gap: 16px;
	}

	.meta.strong {
		color: var(--text-dim);
	}

	@media (max-width: 900px) {
		.filters {
			padding: 16px;
		}

		input[type='search'] {
			width: 100%;
		}

		.line {
			flex-wrap: wrap;
		}

		.head {
			display: none;
		}

		.row {
			height: auto;
			padding: 12px 16px;
			display: grid;
			grid-template-columns: 1fr auto;
			gap: 6px 12px;
			align-items: start;
		}

		.col-card {
			width: auto;
			grid-column: 1 / -1;
			white-space: normal;
		}

		.col-kinds,
		.col-gear,
		.col-tags {
			width: auto;
		}

		.col-gear,
		.col-tags {
			grid-column: 1;
		}

		.col-fav,
		.col-drawn,
		.col-by {
			width: auto;
			grid-column: 2;
			text-align: right;
		}

		.rail {
			padding: 0 16px;
		}
	}
</style>
