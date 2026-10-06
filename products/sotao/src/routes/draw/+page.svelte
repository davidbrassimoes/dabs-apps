<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { invalidateAll } from '$app/navigation';
	import {
		RELAXATION_LADDER,
		drawSingle,
		drawSpread,
		rerollSlot,
		type DrawResult,
		type Pool
	} from '$lib/draw';
	import { SINGLE_KIND, SPREAD_SLOTS } from '$lib/kinds';
	import { createOutbox, indexedDbStore, type DrawRecord } from '$lib/outbox';
	import { isStale, readCachedPool, refreshPool, withRecent } from '$lib/pool';
	import DrawCard from '$lib/components/DrawCard.svelte';
	import DrawFiltersPanel from '$lib/components/DrawFilters.svelte';
	import type { TonightDraw } from '$lib/server/draws';

	let { data } = $props();

	let mode = $derived(page.url.searchParams.get('mode') === 'spread' ? 'spread' : 'single');

	// Seeded from the server render so the first draw needs no network at all.
	let pool = $state<Pool>(untrack(() => data.pool));
	let cached = $state(false);
	let offline = $state(false);

	let current = $state<DrawResult | null>(null);
	let locked = $state<(string | null)[]>([null, null, null]);

	// Tonight's draws, for the back/forward arrows. Stepping through records
	// nothing — it is a plain read (§6).
	let history = $state<DrawResult[]>([]);
	let historyIndex = $state(-1);

	let outbox = createOutbox({
		store: indexedDbStore(),
		post: async (record) => {
			const response = await fetch('/api/draws', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(record)
			});
			return { ok: response.ok, status: response.status };
		}
	});

	const byId = $derived(new Map(pool.cards.map((c) => [c.id, c])));

	/** Rebuilds a stored draw into something the screen can show. */
	function fromRecord(record: TonightDraw): DrawResult {
		return {
			mode: record.mode,
			slots: record.cards
				.slice()
				.sort((a, b) => a.slot - b.slot)
				.map((c) => ({
					kind: c.slotKind,
					card: byId.get(c.cardId) ?? null,
					relaxation: 'none' as const,
					problem: null
				})),
			relaxation: 'none'
		};
	}

	onMount(() => {
		history = data.tonight.filter((d) => d.mode === mode).map(fromRecord);
		historyIndex = history.length - 1;
		// Show tonight's last draw immediately rather than the idle state, so
		// coming back to the screen picks up where it left off.
		if (historyIndex >= 0) current = history[historyIndex];

		void (async () => {
			const stored = await readCachedPool();
			if (stored && stored.syncedAt > pool.syncedAt) pool = stored;

			const fresh = await refreshPool();
			if (fresh) {
				pool = fresh;
				offline = false;
			} else {
				cached = true;
			}
			void outbox.flush();
		})();

		const onOnline = () => {
			offline = false;
			void (async () => {
				await outbox.flush();
				const fresh = await refreshPool();
				if (fresh) {
					pool = fresh;
					cached = false;
				}
			})();
		};
		const onOffline = () => (offline = true);

		// Refresh when focus returns after more than five minutes (§11).
		const onFocus = () => {
			if (!isStale(pool)) return;
			void refreshPool().then((fresh) => {
				if (fresh) {
					pool = fresh;
					cached = false;
				}
			});
		};

		window.addEventListener('online', onOnline);
		window.addEventListener('offline', onOffline);
		window.addEventListener('focus', onFocus);
		offline = !navigator.onLine;

		return () => {
			window.removeEventListener('online', onOnline);
			window.removeEventListener('offline', onOffline);
			window.removeEventListener('focus', onFocus);
		};
	});

	function uuid(): string {
		return crypto.randomUUID();
	}

	function toRecord(result: DrawResult, id: string, at: number): DrawRecord {
		return {
			uuid: id,
			drawnAt: at,
			mode: result.mode,
			cards: result.slots
				.map((slot, index) => ({
					slot: index,
					slotKind: slot.kind,
					cardId: slot.card?.id ?? ''
				}))
				.filter((c) => c.cardId)
		};
	}

	let drawUuid = $state<string | null>(null);

	/** Instant: the draw happens here, from the cached pool. The record follows. */
	function draw() {
		const result = mode === 'spread' ? drawSpread(pool, { locked }) : drawSingle(pool);

		current = result;
		history = [...history.slice(0, historyIndex + 1), result];
		historyIndex = history.length - 1;

		const drawn = result.slots.map((s) => s.card?.id).filter((id): id is string => Boolean(id));
		if (drawn.length === 0) return;

		// Suppression has to work offline, so the cached recent list is updated
		// locally rather than waiting for the server to confirm (§11).
		pool = withRecent(pool, drawn);

		const id = uuid();
		drawUuid = id;
		void outbox.enqueue(toRecord(result, id, Date.now()));
	}

	/** Updates the existing draw_cards row rather than creating a new draw (§6). */
	function reroll(index: number) {
		if (!current) return;
		const result = rerollSlot(pool, current, index);
		current = result;
		history = [...history.slice(0, historyIndex), result];
		historyIndex = history.length - 1;

		const card = result.slots[index].card;
		if (!card || !drawUuid) return;

		pool = withRecent(pool, [card.id]);
		void outbox.enqueue(toRecord(result, drawUuid, Date.now()));
	}

	function toggleLock(index: number) {
		const id = current?.slots[index]?.card?.id ?? null;
		locked = locked.map((value, i) => (i === index ? (value ? null : id) : value));
	}

	function step(delta: number) {
		const next = historyIndex + delta;
		if (next < 0 || next >= history.length) return;
		historyIndex = next;
		current = history[next];
	}

	/**
	 * Patches one card's favourite flag wherever a DrawResult is holding a
	 * reference to it — drawn slots hold a snapshot of the card at draw time,
	 * not a live lookup into pool.cards, so updating the pool alone would leave
	 * the star on screen stale until the next draw.
	 */
	function patchFavourite(result: DrawResult, cardId: string, favorite: boolean): DrawResult {
		if (!result.slots.some((s) => s.card?.id === cardId)) return result;
		return {
			...result,
			slots: result.slots.map((s) =>
				s.card?.id === cardId ? { ...s, card: { ...s.card, favorite } } : s
			)
		};
	}

	async function toggleFavourite(cardId: string) {
		const card = byId.get(cardId);
		if (!card) return;
		const nextFavorite = !card.favorite;

		const response = await fetch('/api/favourite', {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ id: cardId, favorite: nextFavorite })
		});
		if (!response.ok) return;

		pool = {
			...pool,
			cards: pool.cards.map((c) => (c.id === cardId ? { ...c, favorite: nextFavorite } : c))
		};
		if (current) current = patchFavourite(current, cardId, nextFavorite);
		history = history.map((r) => patchFavourite(r, cardId, nextFavorite));
		void invalidateAll();
	}

	// Bound on the page, never on window, and never while focus is in an input (§9).
	function onKey(event: KeyboardEvent) {
		const target = event.target as HTMLElement | null;
		if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return;
		if (event.metaKey || event.ctrlKey || event.altKey) return;

		if (event.key === ' ') {
			event.preventDefault();
			draw();
		} else if (event.key === 'ArrowLeft') {
			event.preventDefault();
			step(-1);
		} else if (event.key === 'ArrowRight') {
			event.preventDefault();
			step(1);
		} else if (mode === 'spread' && ['1', '2', '3'].includes(event.key)) {
			event.preventDefault();
			toggleLock(Number(event.key) - 1);
		} else if (event.key.toLowerCase() === 'f') {
			const id = current?.slots[0]?.card?.id;
			if (id) {
				event.preventDefault();
				void toggleFavourite(id);
			}
		}
	}

	let poolCount = $derived(
		mode === 'spread'
			? SPREAD_SLOTS.map((k) => pool.cards.filter((c) => c.kinds.includes(k)).length).join(' · ')
			: String(pool.cards.filter((c) => c.kinds.includes(SINGLE_KIND)).length)
	);

	let filterSummary = $derived(
		[
			pool.filters.tags.length ? `Tags: ${pool.filters.tags.join(', ')}` : 'Tags: any',
			pool.filters.inRackOnly ? 'In the rack only' : 'Whole inventory',
			pool.filters.favouritesOnly ? 'Favourites only' : null
		]
			.filter(Boolean)
			.join(' · ')
	);

	// Three mode: a locked slot keeps its card, so the button says how many are drawn.
	let drawLabel = $derived.by(() => {
		if (mode !== 'spread') return 'Draw';
		const kept = locked.filter((id) => id !== null).length;
		return kept === 1 ? 'Draw two' : kept === 2 ? 'Draw one' : 'Draw';
	});

	let showFilters = $state(false);
	let availableTags = $derived([...new Set(pool.cards.flatMap((c) => c.tags))].sort());

	async function saveFilters(next: typeof pool.filters) {
		showFilters = false;
		pool = { ...pool, filters: next };
		await fetch('/api/draw-filters', {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify(next)
		});
		// The filter change belongs to every future draw, not the one on screen
		// right now — current stays as it is until the owner draws again.
	}

	// Only slots that actually drew a card via a relaxed step count as a
	// relaxation worth announcing here — a slot with no card explains itself
	// with its own message in DrawCard, and repeating that claim at the top
	// would be wrong when the real reason is "no cards of this kind exist" (§6).
	let relaxationNote = $derived.by(() => {
		if (!current) return null;
		const succeeded = current.slots.filter((s) => s.card && s.relaxation !== 'none');
		if (succeeded.length === 0) return null;

		const worst = succeeded.reduce((worst, s) =>
			RELAXATION_LADDER.indexOf(s.relaxation) > RELAXATION_LADDER.indexOf(worst.relaxation)
				? s
				: worst
		).relaxation;

		switch (worst) {
			case 'window10':
				return 'Pool was thin — suppression shortened to the last 10 draws.';
			case 'noWindow':
				return 'Pool was thin — repeat suppression dropped for this draw.';
			case 'noInRack':
				return 'Pool was thin — drew from gear that is not in the rack.';
			default:
				return null;
		}
	});
</script>

<svelte:head><title>Draw — Sótão</title></svelte:head>
<svelte:window onkeydown={onKey} />

<div class="screen">
	<div class="info">
		<a href={resolve('/gear')} class="rack">{data.rack.inRack} of {data.rack.owned} in the rack</a>
		{#if offline || cached}
			<span class="status">{offline ? 'Offline · cached' : 'Cached'}</span>
		{:else if history.length > 0}
			<span class="status">{Math.max(historyIndex, 0) + 1} / {history.length} tonight</span>
		{/if}
	</div>

	<div class="strip">
		<span class="label lg">{mode === 'spread' ? 'Recipe' : 'Pool'}</span>
		{#if mode === 'spread'}
			<span class="recipe lg">
				{#each SPREAD_SLOTS as slot (slot)}
					<span class="slot-chip" style="--slot: var(--kind-{slot})">{slot}</span>
				{/each}
			</span>
			<span class="meta lg">{poolCount} cards</span>
			<span class="meta sm">{SPREAD_SLOTS.join(' · ')}</span>
		{:else}
			<span class="meta">Ideas · {poolCount} cards</span>
		{/if}
		<span class="meta lg">{filterSummary} · Last 30 draws suppressed</span>
		<button
			type="button"
			class="panel-button filters-button"
			aria-expanded={showFilters}
			onclick={() => (showFilters = !showFilters)}
		>
			Edit filters
		</button>

		{#if showFilters}
			<DrawFiltersPanel
				filters={pool.filters}
				{availableTags}
				onclose={() => (showFilters = false)}
				onsave={saveFilters}
			/>
		{/if}
	</div>

	{#if relaxationNote}
		<p class="relaxed" role="status">
			<span class="lg">{relaxationNote}</span><span class="sm">Pool was thin — rules relaxed.</span>
		</p>
	{/if}

	<div class="board">
		{#if !current}
			<div class="idle">
				<p class="display">
					{mode === 'spread' ? 'Three slots, one idea.' : 'One idea at a time.'}
				</p>
				<p class="meta">Press Space, or hit Draw.</p>
			</div>
		{:else}
			{#each current.slots as slot, index (index)}
				<DrawCard
					{slot}
					{index}
					compact={mode === 'spread'}
					locked={locked[index] !== null}
					showLock={mode === 'spread'}
					onreroll={() => reroll(index)}
					onlock={() => toggleLock(index)}
					onfavourite={(id) => toggleFavourite(id)}
				/>
			{/each}
		{/if}
	</div>

	<div class="controls">
		<div class="history">
			<button
				type="button"
				aria-label="Previous draw"
				class="step"
				disabled={historyIndex <= 0}
				onclick={() => step(-1)}
			>
				<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<path
						d="M15 5l-7 7 7 7"
						stroke="currentColor"
						stroke-width="2.2"
						stroke-linecap="round"
						stroke-linejoin="round"
					/>
				</svg>
			</button>
			<button
				type="button"
				aria-label="Next draw"
				class="step next"
				disabled={historyIndex >= history.length - 1}
				onclick={() => step(1)}
			>
				<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<path
						d="M9 5l7 7-7 7"
						stroke="currentColor"
						stroke-width="2.2"
						stroke-linecap="round"
						stroke-linejoin="round"
					/>
				</svg>
			</button>
		</div>
		<button type="button" class="draw" onclick={draw}>{drawLabel}</button>
	</div>

	<div class="keys">
		<span class="meta"><kbd>Space</kbd> {mode === 'spread' ? 'Draw' : 'Draw again'}</span>
		<span class="meta">
			<kbd>{mode === 'spread' ? '1 2 3' : '← →'}</kbd>
			{mode === 'spread' ? 'Keep a slot' : "Tonight's history"}
		</span>
	</div>
</div>

<style>
	.screen {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		min-height: 0;
		width: 100%;
		max-width: 560px;
		margin: 0 auto;
		box-sizing: border-box;
		padding: 0 16px;
	}

	.info {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 12px;
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
	}

	.rack,
	.status {
		display: flex;
		align-items: center;
		min-height: 44px;
		font-variant-numeric: tabular-nums;
		text-decoration: none;
	}

	.strip {
		position: relative;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 6px 12px;
		padding-bottom: 8px;
	}

	.recipe {
		display: flex;
		align-items: center;
		gap: 6px;
	}

	.slot-chip {
		font-family: var(--font-mono);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--slot);
		background: var(--panel);
		border-radius: 999px;
		padding: 4px 10px;
	}

	.strip .panel-button {
		font-size: 13px;
	}

	.sm {
		display: none;
	}

	/* A phone keeps the strip to one line: what the pool is, and the way to the filters. */
	@media (max-width: 720px) {
		.lg {
			display: none;
		}

		.sm {
			display: inline;
		}

		.strip {
			flex-wrap: nowrap;
			justify-content: space-between;
			padding-bottom: 4px;
		}

		.relaxed {
			margin: 0 0 4px;
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
		}
	}

	.relaxed {
		margin: 0 0 8px;
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text);
	}

	.board {
		flex-grow: 1;
		min-height: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding-top: 4px;
	}

	.idle {
		align-self: center;
		margin: auto 0;
		text-align: center;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.idle .display {
		margin: 0;
		font-size: 32px;
		line-height: 1.1;
		color: var(--text-dim);
	}

	.controls {
		display: flex;
		gap: 10px;
		padding: 14px 0 12px;
	}

	.history {
		display: flex;
		gap: 10px;
	}

	.step {
		width: 64px;
		height: 76px;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 0;
		border-radius: 18px;
		background: var(--panel);
		color: var(--text);
		cursor: pointer;
	}

	.step:disabled {
		color: var(--text-dim);
		opacity: 0.6;
		cursor: not-allowed;
	}

	/* Forward is for tonight's history; a phone has the room for Draw alone. */
	.step.next {
		display: none;
	}

	.draw {
		flex-grow: 1;
		height: 76px;
		border: 0;
		border-radius: 18px;
		background: var(--lamp);
		color: var(--lamp-ink);
		font-family: var(--font-sans);
		font-size: 30px;
		font-weight: 800;
		cursor: pointer;
	}

	.keys {
		display: none;
	}

	@media (min-width: 721px) {
		.screen {
			padding-bottom: 24px;
		}

		.step.next {
			display: flex;
		}

		.keys {
			display: flex;
			justify-content: center;
			gap: 24px;
		}
	}

	kbd {
		font-family: var(--font-mono);
		font-size: 12px;
		font-weight: 700;
		color: var(--text);
		background: var(--panel);
		border-radius: 6px;
		padding: 2px 8px;
	}
</style>
