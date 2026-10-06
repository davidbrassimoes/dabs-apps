<script lang="ts">
	let { data, form } = $props();
</script>

<svelte:head><title>Sign in — Sótão</title></svelte:head>

<div class="ground">
	<form method="POST" class="panel">
		<div class="name">
			<span class="wordmark">sótão</span>
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
		padding: 16px;
		box-sizing: border-box;
	}

	.panel {
		width: 100%;
		max-width: 380px;
		box-sizing: border-box;
		background: var(--panel);
		border-radius: 18px;
		padding: 32px 24px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}

	.name {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.wordmark {
		font-size: 32px;
		font-weight: 800;
		letter-spacing: -0.02em;
		color: var(--text);
		line-height: 1;
	}

	.subtitle {
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--text-dim);
	}

	.lede {
		margin: -8px 0 4px;
		font-size: 16px;
		line-height: 1.5;
		color: var(--text-dim);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	input[type='password'] {
		background: var(--ground);
		border: 2px solid transparent;
		border-radius: 16px;
		color: var(--text);
		font-family: var(--font-sans);
		font-size: 16px;
		padding: 0 16px;
		min-height: 48px;
		box-sizing: border-box;
	}

	input[aria-invalid='true'] {
		border-color: var(--kind-constraint);
	}

	.error {
		margin: 0;
		font-family: var(--font-mono);
		font-size: 13px;
		color: var(--kind-constraint);
	}

	button {
		background: var(--lamp);
		border: none;
		border-radius: 18px;
		color: var(--lamp-ink);
		font-family: var(--font-sans);
		font-size: 20px;
		font-weight: 800;
		min-height: 56px;
		cursor: pointer;
	}
</style>
