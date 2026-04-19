// @source frontend/src/lib/components/PerfGenerator.svelte
// @lines 222-274
// @note $derived analysis · $effect tabOverrides 동기화 · uniqueFields 중복 제거 · setFieldRole
// @synced 2026-04-19T09:32:45.544Z

	const analysis: AnalysisResult = $derived.by(() => analyzeJson(jsonInput));

	// Init tab overrides when tabs change
	$effect(() => {
		const tabs = analysis.tabs;
		untrack(() => {
			const newOverrides: Record<string, { label: string; yAxisUnit: string }> = {};
			for (const tab of tabs) {
				newOverrides[tab.key] = tabOverrides[tab.key] ?? {
					label: tab.label,
					yAxisUnit: tab.yAxisUnit
				};
			}
			tabOverrides = newOverrides;
		});
	});

	const mergedTabs = $derived(
		analysis.tabs.map((t) => ({
			...t,
			label: tabOverrides[t.key]?.label ?? t.label,
			yAxisUnit: tabOverrides[t.key]?.yAxisUnit ?? t.yAxisUnit
		}))
	);

	// Collect unique fields for the field mapping table (deduplicated by path)
	const uniqueFields = $derived.by(() => {
		const seen = new Set<string>();
		const result: FieldNode[] = [];
		for (const f of analysis.allFields) {
			const ps = pathStr(f.path);
			if (!seen.has(ps)) {
				seen.add(ps);
				result.push(f);
			}
		}
		// Also include cycle field if it's a top-level field not in tabs
		if (analysis.cycleField) {
			const ps = pathStr(analysis.cycleField.path);
			if (!seen.has(ps)) {
				seen.add(ps);
				result.push(analysis.cycleField);
			}
		}
		return result;
	});

	function setFieldRole(field: FieldNode, role: FieldRole) {
		const ps = pathStr(field.path);
		fieldRoleOverrides[ps] = role;
		// Force re-analysis by triggering reactivity
		fieldRoleOverrides = { ...fieldRoleOverrides };
	}
