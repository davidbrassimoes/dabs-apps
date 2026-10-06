<script lang="ts">
	import { untrack } from 'svelte';
	import type { DrawFilters } from '$lib/draw';

	// Filters are edited from the draw screen and applied to every draw (§6).
	// No mockup draws this panel, so the layout here is mine — a small popover
	// off the "Edit filters" button rather than a whole extra screen.
	type Props = {
		filters: DrawFilters;
		availableTags: string[];
		onclose: () => void;
		onsave: (next: DrawFilters) => void;
	};
	let { filters, availableTags, onclose, onsave }: Props = $props();

	// Seeded once, deliberately: this is a draft the owner is editing, not a
	// live mirror of the parent's filters.
	let inRackOnly = $state(untrack(() => filters.inRackOnly));
	let favouritesOnly = $state(untrack(() => filters.favouritesOnly));
	let tags = $state<string[]>(untrack(() => [...filters.tags]));

	function toggleTag(tag: string) {
		tags = tags.includes(tag) ? tags.filter((t) => t !== tag) : [...tags, tag];
	}

	function apply() {
		onsave({ inRackOnly, favouritesOnly, tags });
	}
</script>

<div class="panel" role="dialog" aria-label="Draw filters">
	<div class="head">
		<span class="label">Draw filters</span>
		<button type="button" class="close" aria-label="Close" onclick={onclose}>×</button>
	</div>

	<label class="row">
		<input type="checkbox" bind:checked={inRackOnly} />
		<span>In the rack only</span>
	</label>

	<label class="row">
		<input type="checkbox" bind:checked={favouritesOnly} />
		<span>Favourites only</span>
	</label>

	{#if availableTags.length > 0}
		<div class="tags-field">
			<span class="label">Tags — any of these, or none for any tag</span>
			<div class="tag-chips">
				{#each availableTags as tag (tag)}
					<button
						type="button"
						class="tag-chip"
						class:on={tags.includes(tag)}
						onclick={() => toggleTag(tag)}
					>
						#{tag}
					</button>
				{/each}
			</div>
		</div>
	{/if}

	<div class="foot">
		<button type="button" class="panel-button" onclick={onclose}>Cancel</button>
		<button type="button" class="apply" onclick={apply}>Apply</button>
	</div>
</div>

<style>
	.panel {
		position: absolute;
		top: 100%;
		right: 0;
		z-index: 20;
		width: 320px;
		max-width: 100%;
		box-sizing: border-box;
		background: var(--panel);
		border: 1px solid var(--line);
		border-radius: 18px;
		box-shadow: 0 12px 32px var(--shadow-pop);
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}

	.head {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.close {
		margin-left: auto;
		width: 44px;
		height: 44px;
		margin-top: -10px;
		margin-right: -10px;
		background: none;
		border: none;
		border-radius: 999px;
		color: var(--text-dim);
		font-size: 22px;
		line-height: 1;
		cursor: pointer;
	}

	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 44px;
		font-size: 16px;
		color: var(--text);
		cursor: pointer;
	}

	input[type='checkbox'] {
		margin: 0;
		width: 20px;
		height: 20px;
		accent-color: var(--lamp);
	}

	.tags-field {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.tag-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.tag-chip {
		min-height: 44px;
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text);
		background: var(--ground);
		border: 0;
		border-radius: 999px;
		padding: 0 14px;
		cursor: pointer;
	}

	.tag-chip.on {
		background: var(--text);
		color: var(--ground);
		font-weight: 700;
	}

	.foot {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 10px;
		border-top: 1px solid var(--line);
		padding-top: 12px;
	}

	.foot :global(.panel-button) {
		background: var(--ground);
	}

	.apply {
		min-height: 44px;
		background: var(--lamp);
		border: none;
		border-radius: 999px;
		color: var(--lamp-ink);
		font-size: 14px;
		font-weight: 800;
		padding: 0 20px;
		cursor: pointer;
	}

	@media (max-width: 720px) {
		.panel {
			position: fixed;
			top: auto;
			bottom: 0;
			left: 0;
			right: 0;
			width: auto;
			max-width: none;
			border-radius: 18px 18px 0 0;
			padding-bottom: calc(16px + env(safe-area-inset-bottom));
		}
	}
</style>
