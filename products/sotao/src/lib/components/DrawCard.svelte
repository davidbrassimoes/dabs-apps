<script lang="ts">
	import { resolve } from '$app/paths';
	import KindTag from './KindTag.svelte';
	import FavouriteStar from './FavouriteStar.svelte';
	import type { DrawSlot } from '$lib/draw';

	type Props = {
		slot: DrawSlot;
		index: number;
		compact?: boolean;
		locked?: boolean;
		showLock?: boolean;
		onreroll?: () => void;
		onlock?: () => void;
		onfavourite?: (id: string) => void;
	};

	let {
		slot,
		index,
		compact = false,
		locked = false,
		showLock = false,
		onreroll,
		onlock,
		onfavourite
	}: Props = $props();

	// The card is the colour of its kind, and the ink on it comes with the fill.
	let kind = $derived(slot.kind ?? slot.card?.kinds[0] ?? 'idea');
</script>

<article
	class="card"
	class:compact
	class:locked
	style="--fill: var(--card-{kind}); --on: var(--card-{kind}-ink)"
>
	<div class="top">
		<span class="kinds">
			{#if slot.kind}
				<KindTag kind={slot.kind} onCard />
			{:else if slot.card}
				{#each slot.card.kinds as kind (kind)}<KindTag {kind} onCard />{/each}
			{/if}
		</span>

		{#if compact && slot.card}
			<button type="button" class="pill" onclick={onreroll}>Reroll</button>
		{/if}

		{#if showLock}
			<button
				type="button"
				class="pill lock"
				class:on={locked}
				aria-pressed={locked}
				aria-label={locked ? 'Unlock this slot' : 'Lock this slot'}
				title="{locked ? 'Unlock' : 'Lock'} — key {index + 1}"
				onclick={onlock}
			>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<rect
						x="5"
						y="11"
						width="14"
						height="10"
						rx="2"
						stroke="currentColor"
						stroke-width="2.2"
					/>
					<path
						d={locked ? 'M8 11V8a4 4 0 0 1 8 0v3' : 'M8 11V8a4 4 0 0 1 7.5-2'}
						stroke="currentColor"
						stroke-width="2.2"
						stroke-linecap="round"
					/>
				</svg>
				{locked ? 'Kept' : 'Keep'}
			</button>
		{:else if slot.card}
			<button
				type="button"
				class="star"
				aria-label="Favourite"
				aria-pressed={slot.card.favorite}
				onclick={() => onfavourite?.(slot.card!.id)}
			>
				<FavouriteStar filled={slot.card.favorite} size={22} />
			</button>
		{/if}
	</div>

	{#if slot.card}
		<p class="text">{slot.card.text}</p>

		<div class="foot">
			<p class="slugs">
				{slot.card.gear.join(' · ')}{#if slot.card.gear.length && slot.card.tags.length}
					<br />{/if}{slot.card.tags.map((t) => `#${t}`).join(' ')}
			</p>
			{#if !compact}
				<a class="pill" href={resolve('/cards/[id]', { id: slot.card.id })}>Edit card</a>
			{/if}
		</div>
	{:else}
		<div class="empty">
			<p class="message">{slot.problem?.message ?? 'Nothing to draw.'}</p>
			{#if slot.problem?.filter === 'inRack'}
				<a class="pill" href={resolve('/gear')}>Open the gear screen</a>
			{:else if slot.problem?.filter === 'kind'}
				<a class="pill" href={resolve('/cards/new')}>Write one</a>
			{:else}
				<a class="pill" href={resolve('/settings')}>Change the filters</a>
			{/if}
		</div>
	{/if}
</article>

<style>
	.card {
		box-sizing: border-box;
		flex: 1 1 0;
		min-height: 0;
		background: var(--fill);
		color: var(--on);
		border-radius: 18px;
		padding: 20px 22px 22px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.card.compact {
		padding: 12px 18px 16px;
		gap: 8px;
	}

	.top {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.kinds {
		flex-grow: 1;
		display: flex;
		gap: 10px;
	}

	.star {
		width: 44px;
		height: 44px;
		margin: -10px -10px -10px 0;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 0;
		border-radius: 999px;
		background: transparent;
		color: inherit;
		cursor: pointer;
	}

	/* A pill with a contour in the card's ink; Kept fills it. */
	.pill {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
		box-sizing: border-box;
		min-height: 44px;
		margin: -8px 0;
		padding: 0 14px;
		border: 1.5px solid currentColor;
		border-radius: 999px;
		background: transparent;
		color: inherit;
		font-family: var(--font-mono);
		font-size: 13px;
		font-weight: 700;
		text-decoration: none;
		cursor: pointer;
	}

	.top .lock {
		margin-right: -8px;
	}

	.lock.on {
		background: var(--ink);
		border-color: var(--ink);
		color: var(--fill);
	}

	.text {
		margin: auto 0;
		font-weight: 700;
		font-size: 32px;
		line-height: 36px;
		letter-spacing: -0.015em;
		text-wrap: balance;
	}

	.compact .text {
		margin: auto 0 0;
		font-size: 24px;
		line-height: 28px;
		letter-spacing: -0.01em;
	}

	.foot {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: 12px;
	}

	.foot .pill {
		margin: 0;
		flex: none;
	}

	.slugs {
		margin: 0;
		font-family: var(--font-mono);
		font-size: 14px;
		line-height: 20px;
		opacity: 0.8;
	}

	.compact .slugs {
		font-size: 13px;
		line-height: 18px;
	}

	.empty {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		justify-content: center;
		gap: 16px;
	}

	.message {
		margin: 0;
		font-size: 22px;
		font-weight: 700;
		line-height: 1.25;
		letter-spacing: -0.01em;
	}

	.empty .pill {
		margin: 0;
	}
</style>
