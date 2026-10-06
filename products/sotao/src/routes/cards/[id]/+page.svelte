<script lang="ts">
	import CardEditor from '$lib/components/CardEditor.svelte';

	let { data, form } = $props();
</script>

<!--
	Keyed so that moving from one card to the next remounts the editor. Without
	it the fields would keep the previous card's values, because the component
	seeds its state once.
-->
{#key data.card?.id ?? 'new'}
	<CardEditor {data} {form} />
{/key}
