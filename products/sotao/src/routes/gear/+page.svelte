<script lang="ts">
	import { enhance } from '$app/forms';
	import { CATEGORY_COLUMNS, CATEGORY_LABELS, CATEGORY_ORDER, slugify } from '$lib/gear';
	import type { GearCategory } from '$lib/server/db/schema';

	let { data, form } = $props();

	// Two columns, split where the mockup splits them: strings/keys/amps on the
	// left, pedals/mics/small on the right.
	function group(category: GearCategory) {
		const items = data.items.filter((g) => g.category === category);
		return {
			category,
			items,
			inRack: items.filter((g) => g.owned && g.inRack).length,
			owned: items.filter((g) => g.owned).length
		};
	}

	let columns = $derived(
		CATEGORY_COLUMNS.map((categories) =>
			categories.map(group).filter((entry) => entry.items.length > 0)
		)
	);

	let adding = $state(false);
	let addName = $state('');
	let addSlug = $state('');
	let slugTouched = $state(false);
	let addCategory = $state<GearCategory>('pedals');
	let addOwned = $state(true);

	// The slug follows the name until it is edited by hand.
	$effect(() => {
		if (!slugTouched) addSlug = slugify(addName);
	});

	$effect(() => {
		if (form && 'added' in form && form.added) {
			adding = false;
			addName = '';
			addSlug = '';
			slugTouched = false;
		}
	});
</script>

<svelte:head><title>Gear — Sótão</title></svelte:head>

<div class="screen">
	<div class="intro">
		<div class="copy">
			<p>
				Tick what is patched and reachable tonight. Draws are limited to cards whose gear is all
				ticked, so a card never asks you to re-cable the room.
			</p>
			<p class="quieter">
				The AR16c, the T5V pair, headphones and the laptop are always in the chain, so they are not
				listed here and never gate a draw. Cards can still name them in their text.
			</p>
		</div>
		<div class="spacer"></div>
		<form method="POST" action="?/bulk" use:enhance>
			<input type="hidden" name="value" value="on" />
			<button type="submit" class="panel-button">Everything</button>
		</form>
		<form method="POST" action="?/bulk" use:enhance>
			<input type="hidden" name="value" value="off" />
			<button type="submit" class="panel-button">Clear</button>
		</form>
	</div>

	{#if form && 'error' in form && form.error}
		<div class="banner error" role="alert">{form.error}</div>
	{:else if form && 'notice' in form && form.notice}
		<div class="banner notice" role="status">
			<span>{form.notice}</span>
			{#if 'undo' in form && form.undo}
				<form method="POST" action="?/owned" use:enhance class="undo">
					<input type="hidden" name="id" value={form.undo} />
					<input type="hidden" name="owned" value="off" />
					<button type="submit">Undo</button>
				</form>
			{/if}
		</div>
	{/if}

	<div class="board">
		{#each columns as column, i (i)}
			<div class="column">
				{#each column as group (group.category)}
					<section>
						<div class="group-head">
							<span class="group-name">{CATEGORY_LABELS[group.category]}</span>
							<div class="spacer"></div>
							<span class="group-count">{group.inRack}/{group.owned}</span>
						</div>

						{#each group.items as item (item.id)}
							<div class="row" class:wishlist={!item.owned}>
								<form
									method="POST"
									action="?/toggle"
									use:enhance
									class="toggle"
									onchange={(event) => {
										if (item.owned) event.currentTarget.requestSubmit();
									}}
								>
									<input type="hidden" name="id" value={item.id} />
									<label for="g-{item.id}">
										<input
											type="checkbox"
											id="g-{item.id}"
											name="inRack"
											checked={item.inRack}
											disabled={!item.owned}
										/>
										<span class="name" class:racked={item.inRack}>{item.name}</span>
									</label>
								</form>

								{#if !item.owned}
									<form method="POST" action="?/owned" use:enhance class="wish">
										<input type="hidden" name="id" value={item.id} />
										<input type="hidden" name="owned" value="on" />
										<button type="submit" class="badge" title="Mark as owned">Wishlist</button>
									</form>
								{/if}
							</div>
						{/each}
					</section>
				{/each}
			</div>
		{/each}
	</div>

	<div class="rail">
		{#if adding}
			<form method="POST" action="?/add" use:enhance class="add-form">
				<label>
					<span class="label">Name</span>
					<input name="name" bind:value={addName} required placeholder="Boss DD-3" />
				</label>
				<label>
					<span class="label">Slug</span>
					<input
						name="id"
						bind:value={addSlug}
						oninput={() => (slugTouched = true)}
						required
						pattern="[a-z0-9][a-z0-9\-]*"
						placeholder="boss-dd-3"
					/>
				</label>
				<label>
					<span class="label">Category</span>
					<select name="category" bind:value={addCategory}>
						{#each CATEGORY_ORDER as category (category)}
							<option value={category}>{CATEGORY_LABELS[category]}</option>
						{/each}
					</select>
				</label>
				<label class="owned-check">
					<input
						type="checkbox"
						name="owned"
						checked={addOwned}
						onchange={() => (addOwned = !addOwned)}
					/>
					<span class="meta">Owned</span>
				</label>
				<button type="submit" class="panel-button">Add</button>
				<button type="button" class="panel-button" onclick={() => (adding = false)}>Cancel</button>
			</form>
		{:else}
			<span class="rail-note"
				>Wishlist gear can be written into cards now — those cards stay out of draws until you own
				it</span
			>
			<div class="spacer"></div>
			<button type="button" class="panel-button" onclick={() => (adding = true)}>Add gear</button>
		{/if}
	</div>
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
		gap: 16px;
	}

	.intro {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		flex-wrap: wrap;
	}

	.copy {
		display: flex;
		flex-direction: column;
		gap: 8px;
		max-width: 520px;
		flex-basis: 100%;
	}

	.copy p {
		margin: 0;
		font-size: 15px;
		line-height: 1.45;
		color: var(--text);
	}

	.copy .quieter {
		font-size: 14px;
		color: var(--text-dim);
	}

	.spacer {
		flex-grow: 1;
	}

	.banner {
		margin: 0;
		padding: 12px 16px;
		border-radius: 16px;
		background: var(--panel);
		font-family: var(--font-mono);
		font-size: 13px;
	}

	.banner.error {
		color: var(--kind-constraint);
	}

	.banner.notice {
		color: var(--text);
		display: flex;
		align-items: center;
		gap: 12px;
	}

	.undo button {
		min-height: 44px;
		background: none;
		border: 1.5px solid currentColor;
		border-radius: 999px;
		color: inherit;
		font-family: var(--font-mono);
		font-size: 13px;
		font-weight: 700;
		padding: 0 14px;
		cursor: pointer;
	}

	.board {
		flex-grow: 1;
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: 16px;
		align-content: start;
	}

	@media (min-width: 721px) {
		.board {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}

	.column {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}

	section {
		display: flex;
		flex-direction: column;
		background: var(--panel);
		border-radius: 18px;
		padding: 12px 16px 8px;
	}

	.group-head {
		display: flex;
		align-items: center;
		gap: 9px;
		padding-bottom: 4px;
	}

	.group-name {
		font-family: var(--font-mono);
		font-size: 13px;
		font-weight: 700;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		color: var(--text);
	}

	.group-count {
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
		font-variant-numeric: tabular-nums;
	}

	.row {
		display: flex;
		align-items: center;
		gap: 10px;
		border-top: 1px solid var(--line);
	}

	.toggle {
		flex-grow: 1;
		min-width: 0;
	}

	.toggle label {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 48px;
		cursor: pointer;
	}

	.row.wishlist .toggle label {
		cursor: default;
	}

	input[type='checkbox'] {
		margin: 0;
		width: 20px;
		height: 20px;
		accent-color: var(--lamp);
		flex-shrink: 0;
	}

	.name {
		font-size: 16px;
		font-weight: 600;
		color: var(--text-dim);
	}

	.name.racked {
		color: var(--text);
	}

	.row.wishlist .name {
		font-weight: 400;
	}

	.badge {
		min-height: 44px;
		background: none;
		font-family: var(--font-mono);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--kind-technique);
		border: 0;
		padding: 0 4px;
		cursor: pointer;
	}

	.badge:hover {
		color: var(--text);
	}

	.rail {
		display: flex;
		align-items: center;
		gap: 14px;
		flex-wrap: wrap;
	}

	.rail-note {
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
		max-width: 52ch;
	}

	.add-form {
		display: flex;
		align-items: flex-end;
		gap: 12px;
		flex-wrap: wrap;
	}

	.add-form label {
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.add-form input:not([type]),
	.add-form select {
		background: var(--panel);
		border: 0;
		border-radius: 16px;
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 16px;
		padding: 0 14px;
		min-height: 48px;
		box-sizing: border-box;
	}

	.owned-check {
		flex-direction: row !important;
		align-items: center;
		gap: 8px !important;
		min-height: 44px;
	}
</style>
