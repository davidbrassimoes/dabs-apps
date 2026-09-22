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
</script>

<div class="card" class:compact class:locked>
	<div class="top">
		{#if slot.kind}
			<KindTag kind={slot.kind} />
		{:else if slot.card}
			{#each slot.card.kinds as kind (kind)}<KindTag {kind} />{/each}
		{/if}

		{#if slot.card?.favorite}
			<FavouriteStar filled size={11} />
		{/if}

		{#if !compact}
			<span class="hair"></span>
		{/if}
		<div class="spacer"></div>

		{#if showLock}
			<button
				type="button"
				class="lock"
				class:on={locked}
				aria-pressed={locked}
				aria-label={locked ? 'Unlock this slot' : 'Lock this slot'}
				title="{locked ? 'Unlock' : 'Lock'} — key {index + 1}"
				onclick={onlock}
			>
				<svg width="11" height="12" viewBox="0 0 11 12" fill="none" aria-hidden="true">
					<path
						d={locked
							? 'M2 5.2V3.6A3.1 3.1 0 0 1 5.5 1a3.1 3.1 0 0 1 3.5 2.6v1.6'
							: 'M2 5.2V3.6A3.1 3.1 0 0 1 5.5 1a3.1 3.1 0 0 1 3.5 2.6'}
						stroke="currentColor"
						stroke-width="1.2"
					/>
					<rect
						x="1"
						y="5.2"
						width="9"
						height="6"
						rx="1"
						stroke="currentColor"
						stroke-width="1.2"
					/>
				</svg>
				{locked ? 'Locked' : 'Lock'}
			</button>
		{/if}
	</div>

	{#if slot.card}
		<p class="text" class:compact>{slot.card.text}</p>

		<div class="chips">
			{#each slot.card.gear as item (item)}
				<span class="gear">{item}</span>
			{/each}
			<div class="spacer"></div>
			{#each slot.card.tags as tag (tag)}
				<span class="tag">#{tag}</span>
			{/each}
		</div>

		<div class="foot">
			{#if compact}
				<button type="button" class="panel-button" onclick={onreroll}>Reroll</button>
			{:else}
				<a class="panel-button" href={resolve('/cards/[id]', { id: slot.card.id })}>Edit card</a>
				<div class="spacer"></div>
				<button
					type="button"
					class="fav"
					class:on={slot.card.favorite}
					onclick={() => onfavourite?.(slot.card!.id)}
				>
					<FavouriteStar filled={slot.card.favorite} size={12} />
					Favourite
				</button>
			{/if}
		</div>
	{:else}
		<div class="empty">
			<p class="message">{slot.problem?.message ?? 'Nothing to draw.'}</p>
			{#if slot.problem?.filter === 'inRack'}
				<a class="panel-button" href={resolve('/gear')}>Open the gear screen</a>
			{:else if slot.problem?.filter === 'kind'}
				<a class="panel-button" href={resolve('/cards/new')}>Write one</a>
			{:else}
				<a class="panel-button" href={resolve('/settings')}>Change the filters</a>
			{/if}
		</div>
	{/if}
</div>

<style>
	.card {
		box-sizing: border-box;
		background: var(--panel);
		border: 1px solid var(--line);
		border-radius: 3px;
		padding: 44px 48px;
		display: flex;
		flex-direction: column;
		gap: 26px;
		width: 880px;
		max-width: 100%;
	}

	.card.compact {
		width: auto;
		padding: 22px;
		gap: 18px;
	}

	.card.locked {
		border-color: var(--kind-treatment);
	}

	.top {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.hair {
		flex-grow: 1;
		height: 1px;
		background: var(--line);
		display: block;
	}

	.spacer {
		flex-grow: 1;
	}

	.lock {
		display: flex;
		align-items: center;
		gap: 6px;
		background: none;
		border: 1px solid var(--line);
		border-radius: 2px;
		padding: 4px 8px;
		color: var(--text-muted);
		font-family: var(--font-mono);
		font-size: 9px;
		font-weight: 600;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		cursor: pointer;
	}

	.lock.on {
		color: var(--kind-treatment);
		border-color: var(--fav-line);
		background: var(--fav-bg);
	}

	.text {
		margin: 0;
		flex-grow: 1;
		display: flex;
		align-items: center;
		font-family: var(--font-display);
		font-size: 42px;
		font-weight: 600;
		line-height: 1.2;
		letter-spacing: -0.012em;
		color: var(--text);
		text-wrap: balance;
	}

	.text.compact {
		display: block;
		font-size: 25px;
		line-height: 1.28;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 7px;
		align-items: center;
	}

	.gear {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--text-dim);
		border: 1px solid var(--chip-line);
		border-radius: 2px;
		padding: 4px 7px;
	}

	.tag {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.1em;
		color: var(--kind-source);
		padding: 5px 2px;
	}

	.foot {
		display: flex;
		align-items: center;
		gap: 10px;
		border-top: 1px solid var(--line-soft);
		padding-top: 22px;
	}

	.card.compact .foot {
		padding-top: 14px;
	}

	.fav {
		display: flex;
		align-items: center;
		gap: 8px;
		background: none;
		border: 1px solid var(--line);
		border-radius: 2px;
		color: var(--text-dim);
		font-family: var(--font-mono);
		font-size: 10px;
		font-weight: 600;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		padding: 9px 14px;
		cursor: pointer;
	}

	.fav.on {
		background: var(--fav-bg);
		border-color: var(--fav-line);
		color: var(--kind-treatment);
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
		font-family: var(--font-display);
		font-size: 20px;
		font-weight: 500;
		line-height: 1.3;
		color: var(--text-muted);
	}

	:global(.card .panel-button) {
		text-decoration: none;
	}

	@media (max-width: 860px) {
		.card,
		.card.compact {
			width: auto;
			padding: 18px;
			gap: 12px;
		}

		.text {
			font-size: 29px;
			line-height: 1.24;
			display: block;
		}

		.text.compact {
			font-size: 22px;
		}

		.lock {
			min-height: 44px;
			padding: 4px 12px;
		}
	}
</style>
