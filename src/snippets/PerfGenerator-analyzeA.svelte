// @source frontend/src/lib/components/PerfGenerator.svelte
// @lines 81-133
// @note analyzeJson Case 1 — object-of-arrays 분기 (arrayKeys 수집 → tabs · cycleField)
// @synced 2026-04-19T10:15:34.672Z

	// --- Top-level JSON Analysis ---
	function analyzeJson(input: string): AnalysisResult {
		if (!input.trim()) return { shape: 'other', tabs: [], cycleField: null, allFields: [], error: undefined };

		let parsed: unknown;
		try {
			parsed = JSON.parse(input);
		} catch (e) {
			return { shape: 'other', tabs: [], cycleField: null, allFields: [], error: `JSON parse error: ${(e as Error).message}` };
		}

		// Case 1: Top-level object → keys with array values become tabs
		if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
			const obj = parsed as Record<string, unknown>;
			const arrayKeys = Object.keys(obj).filter((k) => Array.isArray(obj[k]) && (obj[k] as unknown[]).length > 0);

			if (arrayKeys.length === 0) {
				return { shape: 'other', tabs: [], cycleField: null, allFields: [], error: 'No array-valued keys found in object' };
			}

			const tabs: TabInfo[] = [];
			const allFields: FieldNode[] = [];
			let cycleField: FieldNode | null = null;

			for (const tabKey of arrayKeys) {
				const arr = obj[tabKey] as unknown[];
				const sample = arr[0];
				if (typeof sample !== 'object' || sample === null) continue;

				const fields = flattenFields(sample as Record<string, unknown>);
				// Apply user overrides
				for (const f of fields) {
					const ps = pathStr(f.path);
					if (fieldRoleOverrides[ps] !== undefined) {
						f.role = fieldRoleOverrides[ps];
					}
				}

				if (!cycleField) {
					cycleField = fields.find((f) => f.role === 'cycle') ?? null;
				}

				tabs.push({
					key: tabKey.toLowerCase(),
					label: capitalize(tabKey),
					yAxisUnit: guessYAxisUnit(tabKey),
					fields
				});
				allFields.push(...fields);
			}

			return { shape: 'object-of-arrays', tabs, cycleField, allFields };
		}
