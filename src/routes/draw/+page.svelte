<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import { page } from '$app/state';
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
	<div class="strip">
		<span class="label">{mode === 'spread' ? 'Recipe' : 'Pool'}</span>
		{#if mode === 'spread'}
			<div class="recipe">
				{#each SPREAD_SLOTS as slot (slot)}
					<span class="slot-chip" style="--ink: var(--kind-{slot})">{slot}</span>
				{/each}
			</div>
			<span class="meta">{poolCount} cards</span>
		{:else}
			<span class="pool-chip">Ideas · {poolCount} cards</span>
		{/if}
		<span class="meta">{filterSummary}</span>
		<button type="button" class="panel-button" onclick={() => (showFilters = !showFilters)}>
			Edit filters
		</button>
		<div class="spacer"></div>
		{#if offline || cached}
			<span class="meta warn">{offline ? 'Offline · cached' : 'Cached'}</span>
		{/if}
		<span class="meta">Last 30 draws suppressed</span>

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
		<p class="relaxed" role="status">{relaxationNote}</p>
	{/if}

	<div class="board" class:spread={mode === 'spread'}>
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

	<div class="rail">
		<div class="keys">
			<div>
				<kbd>Space</kbd><span class="meta">{mode === 'spread' ? 'Draw all' : 'Draw again'}</span>
			</div>
			<div>
				<kbd>{mode === 'spread' ? '1 2 3' : '← →'}</kbd>
				<span class="meta">{mode === 'spread' ? 'Lock a slot' : "Tonight's history"}</span>
			</div>
		</div>

		<div class="big">
			<button type="button" class="draw" onclick={draw}>Draw</button>
		</div>

		<div class="history">
			<button
				type="button"
				aria-label="Previous draw"
				class="step"
				disabled={historyIndex <= 0}
				onclick={() => step(-1)}
			>
				<svg width="10" height="10" viewBox="0 0 10 10" fill="none" aria-hidden="true">
					<path d="M6.5 1 2.5 5l4 4" stroke="currentColor" stroke-width="1.3" />
				</svg>
			</button>
			<button
				type="button"
				aria-label="Next draw"
				class="step"
				disabled={historyIndex >= history.length - 1}
				onclick={() => step(1)}
			>
				<svg width="10" height="10" viewBox="0 0 10 10" fill="none" aria-hidden="true">
					<path d="M3.5 1l4 4-4 4" stroke="currentColor" stroke-width="1.3" />
				</svg>
			</button>
			<span class="meta">{historyIndex >= 0 ? `Draw ${historyIndex + 1}` : 'No draws yet'}</span>
		</div>
	</div>
</div>

<style>
	.screen {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	.strip {
		position: relative;
		height: 46px;
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 0 36px;
		background: var(--rail);
		border-bottom: 1px solid var(--line-soft);
		display: flex;
		align-items: center;
		gap: 14px;
	}

	.recipe {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.slot-chip {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--ink);
		border: 1px solid var(--tag-line);
		border-radius: 2px;
		padding: 4px 9px;
	}

	.pool-chip {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--text);
		border: 1px solid var(--line);
		border-radius: 2px;
		padding: 4px 9px;
	}

	.panel-button {
		padding: 5px 10px;
		text-decoration: none;
	}

	.spacer {
		flex-grow: 1;
	}

	.warn {
		color: var(--kind-treatment);
	}

	.relaxed {
		margin: 0;
		padding: 8px 36px;
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--kind-treatment);
		border-bottom: 1px solid var(--line-soft);
	}

	.board {
		flex-grow: 1;
		box-sizing: border-box;
		padding: 44px 36px;
		display: flex;
		justify-content: center;
		align-items: stretch;
	}

	.board.spread {
		padding: 34px 36px;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 22px;
	}

	.idle {
		align-self: center;
		text-align: center;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.idle .display {
		margin: 0;
		font-family: var(--font-display);
		font-variant-numeric: lining-nums;
		font-size: 34px;
		font-weight: 600;
		color: var(--text-muted);
	}

	.rail {
		height: 96px;
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 0 36px;
		background: var(--rail);
		border-top: 1px solid var(--line);
		display: flex;
		align-items: center;
		gap: 24px;
	}

	.keys {
		display: flex;
		flex-direction: column;
		gap: 5px;
		width: 210px;
	}

	.keys div {
		display: flex;
		align-items: center;
		gap: 9px;
	}

	kbd {
		font-family: var(--font-mono);
		font-size: 9px;
		font-weight: 600;
		letter-spacing: 0.1em;
		color: var(--text-dim);
		border: 1px solid var(--line);
		border-radius: 2px;
		padding: 3px 8px;
	}

	.big {
		flex-grow: 1;
		display: flex;
		justify-content: center;
	}

	.draw {
		background: var(--text);
		border: none;
		border-radius: 3px;
		color: var(--ground);
		font-family: var(--font-display);
		font-variant-numeric: lining-nums;
		font-size: 17px;
		font-weight: 700;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		padding: 17px 52px;
		cursor: pointer;
	}

	.history {
		width: 210px;
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 10px;
	}

	.step {
		background: none;
		border: 1px solid var(--line);
		border-radius: 2px;
		color: var(--text-dim);
		padding: 8px 10px;
		line-height: 0;
		cursor: pointer;
	}

	.step:disabled {
		border-color: var(--line-soft);
		color: var(--text-muted);
		cursor: not-allowed;
	}

	@media (max-width: 860px) {
		.strip {
			height: auto;
			padding: 10px 16px;
			flex-wrap: wrap;
			gap: 8px 12px;
		}

		.board,
		.board.spread {
			padding: 16px;
			display: flex;
			flex-direction: column;
			gap: 12px;
		}

		.relaxed {
			padding: 8px 16px;
		}

		.rail {
			height: auto;
			padding: 16px 16px 24px;
			flex-direction: column;
			align-items: stretch;
			gap: 12px;
		}

		.keys {
			display: none;
		}

		.draw {
			width: 100%;
			padding: 18px 20px;
		}

		.history {
			width: auto;
			justify-content: space-between;
		}

		.step {
			padding: 12px 15px;
		}
	}
</style>
