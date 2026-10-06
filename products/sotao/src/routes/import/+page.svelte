<script lang="ts">
	import { resolve } from '$app/paths';
	import KindLabel from '$lib/components/KindLabel.svelte';
	import type { Kind } from '$lib/vocab';

	type ImportRowClass = 'new' | 'updated' | 'unchanged' | 'error';
	type ImportRow = {
		index: number;
		line: number;
		class: ImportRowClass;
		id: string | null;
		text: string | null;
		kinds: string[];
		gear: string[];
		tags: string[];
		favorite: boolean;
		author: string | null;
		status: string;
		changed: string[];
		issue: { line: number; cardIndex: number; message: string } | null;
	};
	type ImportReport = {
		rows: ImportRow[];
		counts: { new: number; updated: number; unchanged: number; error: number };
		fileError: string | null;
	};

	const EXAMPLE = `# sótão — import v1
version: 1
defaults:
  author: claude
cards:
  - text: >-
      Play the RD-08 through the Pitch Fork+ at plus one octave,
      reamp it into the AC30, and mic the amp from the staircase
      opening.
    kinds: [idea]
    gear: [rd-08, pitch-fork-plus, ac30, cm25]
    tags: [reamping, room]

  - text: Casio SK-1, sampled from something already in the room.
    kinds: [idea, source]
    gear: [sk-1]
    tags: [sampling, lo-fi]
    favorite: true

  - text: Mono. All of it.
    kinds: [constraint]
`;

	let source = $state(EXAMPLE);
	let fileLabel = $state('');
	let report = $state<ImportReport | null>(null);
	let previewing = $state(false);
	let committing = $state(false);
	let committed = $state<{ created: number; updated: number } | null>(null);
	let fileInput = $state<HTMLInputElement>();

	let lines = $derived(source.split('\n'));
	let lineNumbers = $derived(Array.from({ length: lines.length }, (_, i) => i + 1));

	const CLASS_LABEL: Record<ImportRowClass, string> = {
		new: 'New',
		updated: 'Updated',
		unchanged: 'Unchanged',
		error: 'Error'
	};

	// `keepCommitted` is only true for the re-preview commit() runs right after
	// a write, so that refresh doesn't erase the "Imported. N new, N updated."
	// banner it just set.
	async function preview(keepCommitted = false) {
		previewing = true;
		if (!keepCommitted) committed = null;
		try {
			const response = await fetch('/api/import/preview', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ yaml: source })
			});
			report = await response.json();
		} finally {
			previewing = false;
		}
	}

	async function commit() {
		if (!report || report.fileError) return;
		committing = true;
		try {
			const response = await fetch('/api/import/commit', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ yaml: source })
			});
			if (!response.ok) return;
			const result = await response.json();
			committed = result.summary;
			// Re-preview so the list now reads everything as Unchanged, proving
			// the write actually landed — without wiping the banner just set.
			await preview(true);
		} finally {
			committing = false;
		}
	}

	function onFile(event: Event) {
		const file = (event.currentTarget as HTMLInputElement).files?.[0];
		if (!file) return;
		fileLabel = `${file.name} · ${(file.size / 1024).toFixed(1)} kB`;
		file.text().then((text) => {
			source = text;
			report = null;
			committed = null;
		});
	}

	async function pasteFromClipboard() {
		try {
			const text = await navigator.clipboard.readText();
			if (text) {
				source = text;
				fileLabel = '';
				report = null;
				committed = null;
			}
		} catch {
			// Clipboard access denied. The textarea is still editable by hand.
		}
	}

	let importCount = $derived((report?.counts.new ?? 0) + (report?.counts.updated ?? 0));
	let canCommit = $derived(Boolean(report) && !report!.fileError && importCount > 0 && !committing);
	let firstError = $derived(report?.rows.find((r) => r.class === 'error')?.issue ?? null);
</script>

<svelte:head><title>Import — Sótão</title></svelte:head>

<div class="screen">
	<div class="split">
		<div class="source-pane">
			<div class="pane-head">
				<span class="label">Source</span>
				<div class="spacer"></div>
				{#if fileLabel}<span class="meta">{fileLabel}</span>{/if}
				<button type="button" class="panel-button" onclick={() => fileInput?.click()}>
					Choose file
				</button>
				<button type="button" class="panel-button" onclick={pasteFromClipboard}>Paste</button>
				<input
					bind:this={fileInput}
					type="file"
					accept=".yaml,.yml,.json"
					class="hidden-input"
					onchange={onFile}
				/>
			</div>

			<div class="editor">
				<div class="gutter">
					{#each lineNumbers as n (n)}
						<span class="line-no">{n}</span>
					{/each}
				</div>
				<textarea
					bind:value={source}
					spellcheck="false"
					oninput={() => {
						report = null;
						committed = null;
					}}></textarea>
			</div>

			<div class="source-foot">
				<button
					type="button"
					class="primary"
					onclick={() => preview()}
					disabled={previewing || !source.trim()}
				>
					{previewing ? 'Checking…' : 'Preview'}
				</button>
			</div>
		</div>

		<div class="preview-pane">
			<div class="pane-head">
				<span class="label">Preview — nothing is saved yet</span>
			</div>

			{#if !report}
				<div class="idle">
					<p class="meta">Paste or choose a file, then Preview.</p>
				</div>
			{:else if report.fileError}
				<div class="file-error" role="alert">
					<span class="label">File error</span>
					<p>{report.fileError}</p>
				</div>
			{:else}
				<div class="tiles">
					<div class="tile new">
						<span class="count">{report.counts.new}</span>
						<span class="tile-label">New</span>
					</div>
					<div class="tile updated">
						<span class="count">{report.counts.updated}</span>
						<span class="tile-label">Updated</span>
					</div>
					<div class="tile unchanged">
						<span class="count">{report.counts.unchanged}</span>
						<span class="tile-label">Unchanged</span>
					</div>
					<div class="tile error">
						<span class="count">{report.counts.error}</span>
						<span class="tile-label">Error</span>
					</div>
				</div>

				<div class="rows">
					{#each report.rows as row (row.index)}
						<div class="row" class:error={row.class === 'error'}>
							<span class="class-badge {row.class}">{CLASS_LABEL[row.class]}</span>
							<span class="row-text" class:dim={row.class === 'error'}>
								{row.text ?? `card ${row.index}`}
							</span>
							{#if row.kinds[0]}
								<KindLabel kind={row.kinds[0] as Kind} />
							{/if}
						</div>
					{/each}
				</div>

				{#if firstError}
					<div class="issue">
						<svg
							width="15"
							height="15"
							viewBox="0 0 15 15"
							fill="none"
							aria-hidden="true"
							class="issue-icon"
						>
							<path d="M7.5 5v4" stroke="currentColor" stroke-width="1.4" />
							<circle cx="7.5" cy="11.2" r="0.9" fill="currentColor" />
							<circle cx="7.5" cy="7.5" r="6.5" stroke="currentColor" stroke-width="1.2" />
						</svg>
						<div class="issue-body">
							<span class="label">Line {firstError.line} · card {firstError.cardIndex}</span>
							<p>{firstError.message}</p>
						</div>
					</div>
				{/if}

				{#if committed}
					<div class="committed" role="status">
						Imported. {committed.created} new, {committed.updated} updated.
					</div>
				{/if}
			{/if}
		</div>
	</div>

	<div class="rail">
		<span class="meta">Matching on card text · re-importing the same file changes nothing</span>
		<div class="spacer"></div>
		<a href={resolve('/cards')} class="panel-button">Cancel</a>
		<button type="button" class="primary" onclick={commit} disabled={!canCommit}>
			{committing ? 'Importing…' : `Import ${importCount} card${importCount === 1 ? '' : 's'}`}
		</button>
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

	.split {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: 16px;
	}

	@media (min-width: 901px) {
		.split {
			grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
		}
	}

	.source-pane,
	.preview-pane {
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		background: var(--panel);
		border-radius: 18px;
		min-height: 280px;
		overflow: hidden;
	}

	.pane-head {
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 8px 16px;
		min-height: 60px;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 8px 10px;
	}

	.pane-head .panel-button {
		background: var(--ground);
	}

	.spacer {
		flex-grow: 1;
	}

	.hidden-input {
		display: none;
	}

	.editor {
		flex-grow: 1;
		box-sizing: border-box;
		display: flex;
		overflow: auto;
		padding: 8px 0 16px;
		min-height: 240px;
		position: relative;
	}

	.gutter {
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		padding: 0 12px 0 16px;
		user-select: none;
	}

	.line-no {
		font-family: var(--font-mono);
		font-size: 13px;
		line-height: 1.7;
		color: var(--text-dim);
		text-align: right;
		font-variant-numeric: tabular-nums;
		display: block;
		min-width: 22px;
	}

	textarea {
		flex-grow: 1;
		box-sizing: border-box;
		border: none;
		background: none;
		color: var(--text);
		font-family: var(--font-mono);
		font-size: 13px;
		line-height: 1.7;
		padding: 0 16px 0 0;
		resize: none;
		white-space: pre;
		min-height: 100%;
	}

	textarea:focus {
		outline: none;
	}

	.source-foot {
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 12px 16px;
		display: flex;
		justify-content: flex-end;
	}

	.idle,
	.file-error {
		flex-grow: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 10px;
		text-align: center;
		padding: 32px;
	}

	.file-error {
		align-items: flex-start;
		text-align: left;
		justify-content: flex-start;
		padding: 16px;
	}

	.file-error p {
		margin: 0;
		color: var(--kind-constraint);
		font-size: 16px;
		line-height: 1.5;
	}

	.tiles {
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 4px 16px 12px;
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
	}

	@media (min-width: 521px) {
		.tiles {
			grid-template-columns: repeat(4, minmax(0, 1fr));
		}
	}

	.tile {
		box-sizing: border-box;
		background: var(--ground);
		border-radius: 14px;
		padding: 12px 14px;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.count {
		font-size: 28px;
		font-weight: 800;
		letter-spacing: -0.02em;
		line-height: 1;
		font-variant-numeric: tabular-nums;
	}

	.tile.new .count {
		color: var(--kind-source);
	}

	.tile.updated .count {
		color: var(--kind-treatment);
	}

	.tile.unchanged .count {
		color: var(--text-dim);
	}

	.tile.error .count {
		color: var(--kind-constraint);
	}

	.tile-label {
		font-family: var(--font-mono);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--text-dim);
	}

	.rows {
		flex-grow: 1;
		box-sizing: border-box;
		padding: 0 16px;
		overflow-y: auto;
		max-height: 420px;
		min-height: 0;
	}

	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 48px;
		border-top: 1px solid var(--line);
	}

	.class-badge {
		width: 84px;
		flex-shrink: 0;
		font-family: var(--font-mono);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.08em;
		text-transform: uppercase;
	}

	.class-badge.new {
		color: var(--kind-source);
	}

	.class-badge.updated {
		color: var(--kind-treatment);
	}

	.class-badge.unchanged {
		color: var(--text-dim);
	}

	.class-badge.error {
		color: var(--kind-constraint);
	}

	.row-text {
		flex-grow: 1;
		font-size: 15px;
		font-weight: 600;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row-text.dim {
		color: var(--text-dim);
	}

	.issue {
		flex-shrink: 0;
		box-sizing: border-box;
		margin: 12px 16px 16px;
		background: var(--ground);
		border-radius: 14px;
		padding: 14px 16px;
		display: flex;
		gap: 12px;
	}

	.issue-icon {
		flex-shrink: 0;
		margin-top: 2px;
		color: var(--kind-constraint);
	}

	.issue-body {
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.issue-body .label {
		color: var(--kind-constraint);
	}

	.issue-body p {
		margin: 0;
		font-size: 15px;
		line-height: 1.5;
		color: var(--text);
	}

	.committed {
		flex-shrink: 0;
		margin: 0 16px 16px;
		padding: 12px 16px;
		background: var(--ground);
		border-radius: 14px;
		font-family: var(--font-mono);
		font-size: 14px;
		color: var(--kind-source);
	}

	.rail {
		display: flex;
		align-items: center;
		gap: 12px;
		flex-wrap: wrap;
	}
</style>
