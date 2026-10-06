<script lang="ts">
	import { untrack } from 'svelte';
	import { resolve } from '$app/paths';
	import { ALL_KINDS, describeKinds } from '$lib/kinds';
	import FavouriteStar from '$lib/components/FavouriteStar.svelte';
	import type { Kind } from '$lib/vocab';
	import type { CardRow } from '$lib/server/cards';
	import type { GearRow } from '$lib/server/gear';
	import type { CardFormValues } from '$lib/server/cardForm';

	// The page keys this on the card id, so navigating from one card to another
	// remounts it and the fields below start from the new card rather than
	// holding the previous one's values.
	type Props = {
		data: { card: CardRow | null; gear: GearRow[]; tags: string[] };
		form: { error?: string; values?: CardFormValues } | null;
	};
	let { data, form }: Props = $props();

	// Read once, deliberately: the key on the page handles the remount, and after
	// this the fields belong to the person editing, not to the load data.
	const initial = untrack(() => data.card);
	const restored = untrack(() => form?.values ?? null);

	let text = $state(restored?.text ?? initial?.text ?? '');
	let kinds = $state<Kind[]>(
		(restored?.kinds as Kind[]) ?? initial?.kinds ?? (initial ? [] : ['idea'])
	);
	let gear = $state<string[]>(restored?.gear ?? initial?.gear ?? []);
	let tags = $state<string[]>(restored?.tags ?? initial?.tags ?? []);
	let favorite = $state(restored?.favorite ?? initial?.favorite ?? false);
	let author = $state(restored?.author ?? initial?.author ?? 'david');
	let status = $state(restored?.status ?? initial?.status ?? 'active');

	let gearDraft = $state('');
	let tagDraft = $state('');
	let gearProblem = $state('');
	let confirmingDelete = $state(false);

	let knownGear = $derived(new Map(data.gear.map((g) => [g.id, g])));
	let sentence = $derived(describeKinds(kinds));
	let canSave = $derived(text.trim().length > 0 && kinds.length > 0);

	function toggleKind(kind: Kind) {
		kinds = kinds.includes(kind) ? kinds.filter((k) => k !== kind) : [...kinds, kind];
	}

	// Free text is rejected: only slugs in the inventory can be added (§9).
	function addGear(raw: string) {
		const slug = raw.trim().toLowerCase();
		if (!slug) return;
		if (!knownGear.has(slug)) {
			gearProblem = `“${slug}” is not in your inventory.`;
			return;
		}
		if (!gear.includes(slug)) gear = [...gear, slug];
		gearDraft = '';
		gearProblem = '';
	}

	function addTag(raw: string) {
		const tag = raw.trim().toLowerCase().replace(/^#/, '');
		if (!tag) return;
		if (!tags.includes(tag)) tags = [...tags, tag];
		tagDraft = '';
	}

	function onGearKey(event: KeyboardEvent) {
		if (event.key === 'Enter' || event.key === ',') {
			event.preventDefault();
			addGear(gearDraft);
		}
	}

	function onTagKey(event: KeyboardEvent) {
		if (event.key === 'Enter' || event.key === ',') {
			event.preventDefault();
			addTag(tagDraft);
		}
	}
</script>

<svelte:head><title>{initial ? 'Edit card' : 'New card'} — Sótão</title></svelte:head>

<div class="drawer">
	<div class="top">
		<span class="title display">{initial ? 'Edit card' : 'New card'}</span>
		{#if initial}<span class="id">{initial.id}</span>{/if}
		<div class="spacer"></div>
		<a href={resolve('/cards')} class="icon" aria-label="Close editor">
			<svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true">
				<path d="M1 1l9 9M10 1l-9 9" stroke="currentColor" stroke-width="1.3" />
			</svg>
		</a>
	</div>

	<form method="POST" action="?/save" class="body" id="card-form">
		{#if form && 'error' in form && form.error}
			<p class="banner" role="alert">{form.error}</p>
		{/if}

		<div class="field">
			<label class="label" for="card-text">Card text</label>
			<textarea id="card-text" name="text" rows="3" bind:value={text} maxlength="500"></textarea>
			<span class="hint">{text.trim().length} of 500</span>
		</div>

		<div class="field">
			<span class="label">Kinds — pick as many as fit</span>
			<div class="kind-grid">
				{#each ALL_KINDS as kind (kind)}
					<label
						class="kind-box"
						class:on={kinds.includes(kind)}
						for="kind-{kind}"
						style="--fill: var(--card-{kind}); --on: var(--card-{kind}-ink); --dot: var(--kind-{kind})"
					>
						<input
							type="checkbox"
							id="kind-{kind}"
							name="kinds"
							value={kind}
							checked={kinds.includes(kind)}
							onchange={() => toggleKind(kind)}
						/>
						<span class="dot"></span>
						<span class="kind-name">{kind}</span>
					</label>
				{/each}
			</div>
			<p class="sentence" aria-live="polite">
				<strong>Idea</strong> makes the card drawable on its own. A spread has three slots — source,
				treatment and constraint. {sentence}
			</p>
		</div>

		<div class="field">
			<label class="label" for="gear-add">Gear</label>
			<div class="chip-row">
				{#each gear as slug (slug)}
					<span class="gear-chip">
						<input type="hidden" name="gear" value={slug} />
						{knownGear.get(slug)?.name ?? slug}
						<button
							type="button"
							aria-label="Remove {slug}"
							onclick={() => (gear = gear.filter((g) => g !== slug))}>×</button
						>
					</span>
				{/each}
				<input
					id="gear-add"
					type="text"
					list="gear-options"
					bind:value={gearDraft}
					onkeydown={onGearKey}
					onblur={() => gearDraft && addGear(gearDraft)}
					placeholder="add gear…"
				/>
				<datalist id="gear-options">
					{#each data.gear as item (item.id)}
						{#if !gear.includes(item.id)}
							<option value={item.id}>{item.name}</option>
						{/if}
					{/each}
				</datalist>
			</div>
			{#if gearProblem}
				<p class="problem" role="alert">
					{gearProblem}
					<a href={resolve('/gear')}>Add it on the gear screen</a>.
				</p>
			{:else}
				<p class="hint">Only gear in your inventory. Unknown names are rejected on import too.</p>
			{/if}
		</div>

		<div class="field">
			<label class="label" for="tags-add">Tags</label>
			<div class="chip-row">
				{#each tags as tag (tag)}
					<span class="tag-chip">
						<input type="hidden" name="tags" value={tag} />
						#{tag}
						<button
							type="button"
							aria-label="Remove {tag}"
							onclick={() => (tags = tags.filter((t) => t !== tag))}>×</button
						>
					</span>
				{/each}
				<input
					id="tags-add"
					type="text"
					list="tag-options"
					bind:value={tagDraft}
					onkeydown={onTagKey}
					onblur={() => tagDraft && addTag(tagDraft)}
					placeholder="add tag…"
				/>
				<datalist id="tag-options">
					{#each data.tags as tag (tag)}
						{#if !tags.includes(tag)}<option value={tag}></option>{/if}
					{/each}
				</datalist>
			</div>
		</div>

		<div class="field">
			<span class="label">Favourite</span>
			<label class="fav-box" class:on={favorite} for="fav-toggle">
				<input
					type="checkbox"
					id="fav-toggle"
					name="favorite"
					checked={favorite}
					onchange={() => (favorite = !favorite)}
				/>
				<FavouriteStar filled={favorite} size={20} />
				<span>A marker only — it does not change how often the card comes up</span>
			</label>
		</div>

		<div class="pair">
			<div class="field">
				<span class="label">Written by</span>
				<div class="radio-row">
					{#each ['david', 'claude'] as who (who)}
						<label
							class="radio"
							class:on={author === who}
							class:claude={who === 'claude'}
							for="by-{who}"
						>
							<input
								type="radio"
								name="author"
								id="by-{who}"
								value={who}
								checked={author === who}
								onchange={() => (author = who)}
							/>
							<span>{who}</span>
						</label>
					{/each}
				</div>
			</div>

			<div class="field">
				<span class="label">Status</span>
				<div class="radio-row">
					{#each ['active', 'archived'] as state (state)}
						<label
							class="radio"
							class:on={status === state}
							class:active={state === 'active'}
							for="st-{state}"
						>
							<input
								type="radio"
								name="status"
								id="st-{state}"
								value={state}
								checked={status === state}
								onchange={() => (status = state)}
							/>
							<span>{state}</span>
						</label>
					{/each}
				</div>
			</div>
		</div>
	</form>

	<div class="rail">
		{#if initial}
			{#if confirmingDelete}
				<span class="confirm">Delete permanently? This cannot be undone.</span>
				<form method="POST" action="?/delete">
					<button type="submit" class="danger">Yes, delete</button>
				</form>
				<button type="button" class="panel-button" onclick={() => (confirmingDelete = false)}>
					Keep
				</button>
			{:else}
				<button type="button" class="danger" onclick={() => (confirmingDelete = true)}
					>Delete</button
				>
				<form method="POST" action={status === 'archived' ? '?/restore' : '?/archive'}>
					<button type="submit" class="panel-button">
						{status === 'archived' ? 'Restore' : 'Archive'}
					</button>
				</form>
			{/if}
		{/if}
		<div class="spacer"></div>
		<a href={resolve('/cards')} class="panel-button">Cancel</a>
		<button type="submit" form="card-form" class="primary" disabled={!canSave}>
			{initial ? 'Save card' : 'Create card'}
		</button>
	</div>
</div>

<style>
	.drawer {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		min-height: 0;
		width: 100%;
		max-width: 640px;
		margin: 0 auto;
		box-sizing: border-box;
		padding: 0 16px 24px;
	}

	.top {
		flex-shrink: 0;
		padding: 10px 0 6px;
		display: flex;
		align-items: center;
		gap: 12px;
	}

	.title {
		font-size: 32px;
	}

	.id {
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
	}

	.spacer {
		flex-grow: 1;
	}

	.icon {
		width: 44px;
		height: 44px;
		border-radius: 999px;
		background: var(--panel);
		color: var(--text);
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.body {
		flex-grow: 1;
		box-sizing: border-box;
		padding: 12px 0 20px;
		display: flex;
		flex-direction: column;
		gap: 24px;
		min-height: 0;
	}

	.banner {
		margin: 0;
		font-family: var(--font-mono);
		font-size: 14px;
		color: var(--kind-constraint);
		background: var(--panel);
		border-radius: 14px;
		padding: 12px 14px;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	textarea {
		box-sizing: border-box;
		width: 100%;
		background: var(--panel);
		border: 0;
		border-radius: 18px;
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 20px;
		font-weight: 700;
		letter-spacing: -0.01em;
		line-height: 1.3;
		padding: 16px 18px;
		resize: vertical;
	}

	.hint,
	.problem,
	.sentence {
		margin: 0;
		font-size: 14px;
		line-height: 1.45;
		color: var(--text-dim);
	}

	.problem {
		color: var(--kind-constraint);
	}

	.sentence strong {
		color: var(--text);
		font-weight: 700;
	}

	.kind-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
	}

	@media (min-width: 721px) {
		.kind-grid {
			grid-template-columns: repeat(3, minmax(0, 1fr));
		}
	}

	.kind-box,
	.radio,
	.fav-box {
		display: flex;
		align-items: center;
		gap: 10px;
		box-sizing: border-box;
		min-height: 48px;
		padding: 0 14px;
		background: var(--panel);
		color: var(--text);
		border-radius: 999px;
		cursor: pointer;
	}

	.kind-box.on {
		background: var(--fill);
		color: var(--on);
	}

	.dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		background: var(--dot);
		flex-shrink: 0;
	}

	.kind-box.on .dot {
		background: var(--on);
	}

	.kind-name {
		font-family: var(--font-mono);
		font-size: 13px;
		font-weight: 700;
		letter-spacing: 0.14em;
		text-transform: uppercase;
	}

	input[type='checkbox'],
	input[type='radio'] {
		margin: 0;
		accent-color: var(--lamp);
		width: 18px;
		height: 18px;
		flex-shrink: 0;
	}

	.kind-box.on input {
		accent-color: var(--on);
	}

	.chip-row {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		align-items: center;
	}

	.gear-chip,
	.tag-chip {
		display: flex;
		align-items: center;
		gap: 4px;
		min-height: 44px;
		box-sizing: border-box;
		font-family: var(--font-mono);
		font-size: 14px;
		background: var(--panel);
		border-radius: 999px;
		padding: 0 4px 0 14px;
		color: var(--text);
	}

	.gear-chip button,
	.tag-chip button {
		width: 36px;
		height: 36px;
		border: 0;
		border-radius: 999px;
		background: none;
		color: var(--text-dim);
		cursor: pointer;
		font-size: 18px;
		line-height: 1;
	}

	.gear-chip button:hover,
	.tag-chip button:hover {
		color: var(--text);
	}

	.chip-row input[type='text'] {
		flex-grow: 1;
		min-width: 140px;
		box-sizing: border-box;
		background: var(--panel);
		border: 0;
		border-radius: 999px;
		color: var(--text);
		font-family: var(--font-mono);
		font-size: 14px;
		padding: 0 16px;
		min-height: 44px;
	}

	.fav-box {
		border-radius: 18px;
		padding: 12px 16px;
		font-size: 15px;
		color: var(--text-dim);
	}

	.fav-box.on {
		color: var(--text);
	}

	.fav-box.on :global(svg) {
		color: var(--lamp);
	}

	.pair {
		display: flex;
		flex-direction: column;
		gap: 24px;
	}

	@media (min-width: 721px) {
		.pair {
			flex-direction: row;
		}
	}

	.pair .field {
		flex-grow: 1;
	}

	.radio-row {
		display: flex;
		gap: 8px;
	}

	.radio {
		flex-grow: 1;
	}

	.radio span {
		font-family: var(--font-mono);
		font-size: 13px;
		font-weight: 700;
		letter-spacing: 0.1em;
		text-transform: uppercase;
	}

	.radio.on {
		background: var(--text);
		color: var(--ground);
	}

	.radio.on input {
		accent-color: var(--ground);
	}

	.rail {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		gap: 12px;
		flex-wrap: wrap;
	}

	.confirm {
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--kind-constraint);
	}

	.danger {
		min-height: 44px;
		background: none;
		border: 1.5px solid currentColor;
		border-radius: 999px;
		color: var(--kind-constraint);
		font-family: var(--font-sans);
		font-size: 14px;
		font-weight: 600;
		padding: 0 18px;
		cursor: pointer;
	}
</style>
