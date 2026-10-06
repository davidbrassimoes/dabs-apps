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
		top: 44px;
		right: 36px;
		z-index: 20;
		width: 320px;
		box-sizing: border-box;
		background: var(--panel);
		border: 1px solid var(--line);
		border-radius: 3px;
		box-shadow: 0 12px 32px rgba(0, 0, 0, 0.4);
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
		background: none;
		border: none;
		color: var(--text-dim);
		font-size: 16px;
		line-height: 1;
		cursor: pointer;
		padding: 2px 6px;
	}

	.row {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 13px;
		color: var(--text);
		cursor: pointer;
	}

	input[type='checkbox'] {
		margin: 0;
		width: 14px;
		height: 14px;
		accent-color: var(--kind-treatment);
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
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.08em;
		color: var(--kind-source);
		background: none;
		border: 1px solid var(--tag-line);
		border-radius: 2px;
		padding: 5px 8px;
		cursor: pointer;
	}

	.tag-chip.on {
		background: var(--kind-source);
		color: var(--ground);
		font-weight: 600;
	}

	.foot {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 10px;
		border-top: 1px solid var(--line-soft);
		padding-top: 12px;
	}

	.apply {
		background: var(--text);
		border: none;
		border-radius: 2px;
		color: var(--ground);
		font-family: var(--font-mono);
		font-size: 10px;
		font-weight: 600;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		padding: 8px 14px;
		cursor: pointer;
	}

	@media (max-width: 860px) {
		.panel {
			position: fixed;
			top: auto;
			bottom: 0;
			left: 0;
			right: 0;
			width: auto;
			border-radius: 3px 3px 0 0;
		}
	}
</style>
