// @source frontend/src/lib/components/JsonTreeView.svelte
// @lines 1-60
// @note parseResult $derived + auto-expand $effect (depth≤2, array limit 5)
// @synced 2026-05-01T01:10:31.187Z

<script lang="ts">
	import { untrack } from 'svelte';

	interface Props {
		jsonString: string;
	}

	let { jsonString }: Props = $props();

	const parseResult = $derived.by(() => {
		if (!jsonString.trim()) return { value: undefined, error: '' };
		try {
			return { value: JSON.parse(jsonString), error: '' };
		} catch (e) {
			return { value: undefined, error: (e as Error).message };
		}
	});

	const parsedValue = $derived(parseResult.value);
	const parseError = $derived(parseResult.error);

	let expandedPaths = $state<Set<string>>(new Set());
	let lastJsonString = '';

	// Auto-expand top 2 depths on new JSON
	$effect(() => {
		const val = parsedValue;
		const currentJson = jsonString;
		untrack(() => {
			if (currentJson === lastJsonString) return;
			lastJsonString = currentJson;
			if (val === undefined) {
				expandedPaths = new Set();
				return;
			}
			const paths = new Set<string>();
			function walk(v: unknown, path: string, depth: number) {
				if (depth > 2) return;
				if (v !== null && typeof v === 'object') {
					paths.add(path);
					if (Array.isArray(v)) {
						for (let i = 0; i < Math.min(v.length, 5); i++) {
							walk(v[i], `${path}[${i}]`, depth + 1);
						}
					} else {
						for (const key of Object.keys(v as Record<string, unknown>)) {
							walk((v as Record<string, unknown>)[key], `${path}.${key}`, depth + 1);
						}
					}
				}
			}
			walk(val, '$', 0);
			expandedPaths = paths;
		});
	});

	function togglePath(path: string) {
		const next = new Set(expandedPaths);
		if (next.has(path)) next.delete(path);
		else next.add(path);
