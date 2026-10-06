<script lang="ts">
	let { data, form } = $props();
</script>

<svelte:head><title>Sign in — Sótão</title></svelte:head>

<div class="ground">
	<form method="POST" class="panel">
		<div class="name">
			<span class="wordmark">Sótão</span>
			<span class="subtitle">studio cards</span>
		</div>
		<p class="lede">One password. It stays signed in for a year.</p>

		<label class="field">
			<span class="label">Password</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input
				type="password"
				name="password"
				autocomplete="current-password"
				autofocus
				required
				aria-invalid={form?.error ? 'true' : undefined}
			/>
		</label>

		<input type="hidden" name="from" value={data.from} />

		{#if form?.error}
			<p class="error" role="alert">{form.error}</p>
		{/if}

		<button type="submit">Enter</button>
	</form>
</div>

<style>
	.ground {
		min-height: 100vh;
		min-height: 100dvh;
		background: var(--ground);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 24px;
		box-sizing: border-box;
	}

	.panel {
		width: 100%;
		max-width: 380px;
		box-sizing: border-box;
		background: var(--panel);
		border: 1px solid var(--line);
		border-radius: 3px;
		padding: 40px 36px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	/* Product name over its function line, as on a faceplate (brand/tokens.md, "Type"). */
	.name {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.wordmark {
		font-family: var(--font-display);
		font-variant-numeric: lining-nums;
		font-size: 30px;
		font-weight: 600;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		color: var(--text);
		line-height: 1;
	}

	.subtitle {
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.29em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.lede {
		margin: -10px 0 4px;
		font-size: 13.5px;
		line-height: 1.55;
		color: var(--text-dim);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	input[type='password'] {
		background: var(--ground);
		border: 1px solid var(--line);
		border-radius: 2px;
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 15px;
		padding: 12px 12px;
		min-height: 44px;
		box-sizing: border-box;
	}

	input[aria-invalid='true'] {
		border-color: var(--kind-constraint);
	}

	.error {
		margin: 0;
		font-family: var(--font-mono);
		font-size: 10px;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--kind-constraint);
	}

	button {
		background: var(--text);
		border: none;
		border-radius: 3px;
		color: var(--ground);
		font-family: var(--font-display);
		font-variant-numeric: lining-nums;
		font-size: 15px;
		font-weight: 700;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		padding: 15px 20px;
		min-height: 44px;
		cursor: pointer;
	}
</style>
