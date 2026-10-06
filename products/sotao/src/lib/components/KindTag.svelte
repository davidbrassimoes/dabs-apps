<script lang="ts">
	import type { Kind } from '$lib/vocab';

	// The kind colours are semantic and carry meaning across every screen, so
	// this one tag is used by the manager, the editor and the draw screen. On a
	// card it takes the card's ink instead (onCard).
	type Props = { kind: Kind; onCard?: boolean };
	let { kind, onCard = false }: Props = $props();
</script>

<span class="tag" class:on-card={onCard} style="--tag: var(--kind-{kind})">{kind}</span>

<style>
	.tag {
		color: var(--tag);
		font-family: var(--font-mono);
		font-size: 13px;
		font-weight: 700;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		white-space: nowrap;
	}

	.tag.on-card {
		color: inherit;
	}
</style>
