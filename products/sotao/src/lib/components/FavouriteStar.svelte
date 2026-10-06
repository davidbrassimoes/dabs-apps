<script lang="ts">
	// A marker only. Nothing about it promises an effect on draw odds (§2).
	// It takes the colour of the text around it, so it sits on a card or a panel.
	type Props = { filled?: boolean; size?: number };
	let { filled = false, size = 13 }: Props = $props();
</script>

<svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
	<path
		d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"
		fill={filled ? 'currentColor' : 'none'}
		stroke="currentColor"
		stroke-width="1.8"
		stroke-linejoin="round"
	/>
</svg>
