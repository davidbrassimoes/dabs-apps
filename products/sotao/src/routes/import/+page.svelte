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
						<svg width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden="true">
							<path d="M7.5 5v4" stroke="var(--kind-constraint)" stroke-width="1.4" />
							<circle cx="7.5" cy="11.2" r="0.9" fill="var(--kind-constraint)" />
							<circle
								cx="7.5"
								cy="7.5"
								r="6.5"
								stroke="var(--kind-constraint)"
								stroke-width="1.2"
							/>
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
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	.split {
		flex-grow: 1;
		box-sizing: border-box;
		display: grid;
		grid-template-columns: minmax(0, 560px) minmax(0, 1fr);
		overflow: hidden;
	}

	.source-pane {
		box-sizing: border-box;
		border-right: 1px solid var(--line);
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}

	.preview-pane {
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}

	.pane-head {
		height: 44px;
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 0 24px;
		background: var(--rail);
		border-bottom: 1px solid var(--line-soft);
		display: flex;
		align-items: center;
		gap: 10px;
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
		padding: 16px 0;
		position: relative;
	}

	.gutter {
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		padding: 0 14px 0 24px;
		user-select: none;
	}

	.line-no {
		font-family: var(--font-mono);
		font-size: 11px;
		line-height: 1.85;
		color: var(--text-faint);
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
		color: var(--text-dim);
		font-family: var(--font-mono);
		font-size: 11.5px;
		line-height: 1.85;
		padding: 0 24px 0 0;
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
		padding: 12px 24px;
		border-top: 1px solid var(--line-soft);
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
		padding: 40px;
	}

	.file-error {
		align-items: flex-start;
		text-align: left;
		justify-content: flex-start;
		padding: 24px;
	}

	.file-error p {
		margin: 0;
		color: var(--kind-constraint);
		font-size: 14px;
		line-height: 1.5;
	}

	.tiles {
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 20px 24px;
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 12px;
	}

	.tile {
		box-sizing: border-box;
		border: 1px solid var(--line);
		border-radius: 2px;
		padding: 13px 14px;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.tile.new {
		border-color: var(--tag-line);
	}

	.tile.updated {
		border-color: var(--fav-line);
	}

	.tile.error {
		border-color: var(--danger-line);
	}

	.count {
		font-family: var(--font-display);
		font-variant-numeric: lining-nums;
		font-size: 27px;
		font-weight: 700;
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
		font-size: 9px;
		font-weight: 600;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--text-dim);
	}

	.rows {
		flex-grow: 1;
		box-sizing: border-box;
		padding: 4px 24px 0;
		overflow-y: auto;
		min-height: 0;
	}

	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		height: 42px;
		border-bottom: 1px solid var(--row-line);
	}

	.class-badge {
		width: 74px;
		flex-shrink: 0;
		font-family: var(--font-mono);
		font-size: 8.5px;
		font-weight: 600;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		border-radius: 2px;
		padding: 4px 0;
		text-align: center;
	}

	.class-badge.new {
		color: var(--kind-source);
		border: 1px solid var(--tag-line);
	}

	.class-badge.updated {
		color: var(--kind-treatment);
		border: 1px solid var(--fav-line);
	}

	.class-badge.unchanged {
		color: var(--text-dim);
		border: 1px solid var(--line);
	}

	.class-badge.error {
		color: var(--kind-constraint);
		border: 1px solid var(--danger-line);
	}

	.row-text {
		flex-grow: 1;
		font-size: 13px;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row-text.dim {
		color: var(--text-muted);
	}

	.issue {
		flex-shrink: 0;
		box-sizing: border-box;
		margin: 16px 24px 20px;
		background: var(--fav-bg);
		border: 1px solid var(--danger-line);
		border-radius: 2px;
		padding: 15px 16px;
		display: flex;
		gap: 13px;
	}

	.issue svg {
		flex-shrink: 0;
		margin-top: 2px;
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
		font-size: 13px;
		line-height: 1.5;
		color: var(--text);
	}

	.committed {
		flex-shrink: 0;
		margin: 0 24px 20px;
		padding: 12px 16px;
		background: var(--status-active-bg);
		border: 1px solid var(--tag-line);
		border-radius: 2px;
		font-family: var(--font-mono);
		font-size: 11px;
		letter-spacing: 0.05em;
		color: var(--kind-source);
	}

	.rail {
		height: 76px;
		flex-shrink: 0;
		box-sizing: border-box;
		padding: 0 36px;
		background: var(--rail);
		border-top: 1px solid var(--line);
		display: flex;
		align-items: center;
		gap: 14px;
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
		cursor: pointer;
		text-decoration: none;
	}

	.primary:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	@media (max-width: 900px) {
		.split {
			grid-template-columns: minmax(0, 1fr);
			grid-auto-rows: minmax(280px, auto);
			overflow-y: auto;
		}

		.source-pane {
			border-right: none;
			border-bottom: 1px solid var(--line);
		}

		.pane-head,
		.tiles,
		.rows {
			padding-left: 16px;
			padding-right: 16px;
		}

		.tiles {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}

		.rail {
			height: auto;
			padding: 12px 16px;
			flex-wrap: wrap;
		}
	}
</style>
