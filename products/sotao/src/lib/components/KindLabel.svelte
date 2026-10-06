<script lang="ts">
	import type { Kind } from '$lib/vocab';

	// A plain-text kind label in its semantic colour — used where a border chip
	// (KindTag) would be too heavy, like the import preview's trailing column.
	type Props = { kind: Kind };
	let { kind }: Props = $props();
</script>

<span style="color: var(--kind-{kind})">{kind}</span>

<style>
	span {
		font-family: var(--font-mono);
		font-size: 9.5px;
		font-weight: 400;
		letter-spacing: 0.1em;
		text-transform: uppercase;
	}
</style>
