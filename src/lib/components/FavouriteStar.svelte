<script lang="ts">
	// A marker only. Nothing about it promises an effect on draw odds (§2).
	type Props = { filled?: boolean; size?: number };
	let { filled = false, size = 13 }: Props = $props();
</script>

<svg width={size} height={size} viewBox="0 0 12 12" fill="none" aria-hidden="true">
	<path
		d="M6 0.6l1.7 3.5 3.8.55-2.75 2.68.65 3.78L6 9.32 2.6 11.11l.65-3.78L.5 4.65l3.8-.55z"
		fill={filled ? 'var(--fav-star)' : 'none'}
		stroke={filled ? 'var(--kind-treatment)' : 'var(--line)'}
		stroke-width="0.9"
	/>
</svg>
