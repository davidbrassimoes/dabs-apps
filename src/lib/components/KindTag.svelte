<script lang="ts">
	import type { Kind } from '$lib/vocab';

	// The kind colours are semantic and carry meaning across every screen (§12),
	// so this one tag is used by the manager, the editor and the draw screen.
	type Props = { kind: Kind };
	let { kind }: Props = $props();
</script>

<span style="color: var(--kind-{kind})">{kind}</span>

<style>
	span {
		font-family: var(--font-mono);
		font-size: 9.5px;
		font-weight: 600;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		white-space: nowrap;
	}
</style>
