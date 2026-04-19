// @source frontend/src/lib/components/PerfGenerator.svelte
// @lines 17-79
// @note $state 정의 · guessYAxisUnit/capitalize · flattenFields 재귀 (4 분기 + role 휴리스틱)
// @synced 2026-04-19T09:49:20.707Z

	// --- State ---
	let jsonInput = $state('');
	let componentName = $state('MyPerf');
	let xAxisUnit = $state('GB');
	let copied = $state(false);
	let rightPanelTab = $state<'code' | 'preview'>('code');
	let jsonPanelTab = $state<'text' | 'tree'>('text');

	// Parsed data for preview
	const parsedData = $derived.by(() => {
		if (!jsonInput.trim()) return undefined;
		try {
			return JSON.parse(jsonInput);
		} catch {
			return undefined;
		}
	});

	// User overrides: keyed by field path string
	let fieldRoleOverrides = $state<Record<string, FieldRole>>({});
	let tabOverrides = $state<Record<string, { label: string; yAxisUnit: string }>>({});

	// --- Utility ---
	function capitalize(s: string): string {
		return s.charAt(0).toUpperCase() + s.slice(1);
	}

	function guessYAxisUnit(key: string): string {
		const k = key.toLowerCase();
		if (/rand/.test(k)) return 'IOPS';
		if (/seq/.test(k)) return 'MB/s';
		if (/time|lat/.test(k)) return 'ms';
		return 'Value';
	}

	function pathStr(path: string[]): string {
		return path.join('.');
	}

	// --- Recursive Field Flattener ---
	function flattenFields(obj: Record<string, unknown>, path: string[] = []): FieldNode[] {
		const nodes: FieldNode[] = [];
		for (const [key, value] of Object.entries(obj)) {
			const currentPath = [...path, key];
			if (value === null || value === undefined) {
				nodes.push({ path: currentPath, key, type: 'null', sample: null, role: 'ignore' });
			} else if (Array.isArray(value) && value.length > 0 && value.every((v) => typeof v === 'number')) {
				nodes.push({ path: currentPath, key, type: 'number[]', sample: value, role: 'data' });
			} else if (typeof value === 'number') {
				const role: FieldRole = /cycle|id|index/i.test(key) ? 'cycle' : 'stat';
				nodes.push({ path: currentPath, key, type: 'number', sample: value, role });
			} else if (typeof value === 'string') {
				nodes.push({ path: currentPath, key, type: 'string', sample: value, role: 'ignore' });
			} else if (typeof value === 'object' && !Array.isArray(value)) {
				// Recurse into nested objects
				nodes.push(...flattenFields(value as Record<string, unknown>, currentPath));
			} else {
				// Arrays of non-numbers, etc.
				nodes.push({ path: currentPath, key, type: 'object', sample: value, role: 'ignore' });
			}
		}
		return nodes;
	}
