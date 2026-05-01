// @source frontend/src/lib/components/PerfGenerator.svelte
// @lines 135-220
// @note analyzeJson Case 2 — array-of-objects 분기 (top fields + nested tabs + 빈 tabKeys fallback)
// @synced 2026-05-01T01:10:31.186Z


		// Case 2: Top-level array → each item's object-valued keys become tabs
		if (Array.isArray(parsed)) {
			if (parsed.length === 0) return { shape: 'other', tabs: [], cycleField: null, allFields: [], error: 'Empty array' };

			const sample = parsed[0];
			if (typeof sample !== 'object' || sample === null) {
				return { shape: 'other', tabs: [], cycleField: null, allFields: [], error: 'Array items are not objects' };
			}

			const sampleObj = sample as Record<string, unknown>;

			// Top-level fields of array items (cycle candidates, etc.)
			const topFields: FieldNode[] = [];
			const tabKeys: string[] = [];

			for (const [key, value] of Object.entries(sampleObj)) {
				if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
					tabKeys.push(key);
				} else if (typeof value === 'number') {
					const role: FieldRole = /cycle|id|index/i.test(key) ? 'cycle' : 'stat';
					topFields.push({ path: [key], key, type: 'number', sample: value, role });
				} else if (typeof value === 'string') {
					topFields.push({ path: [key], key, type: 'string', sample: value, role: 'ignore' });
				} else if (value === null) {
					topFields.push({ path: [key], key, type: 'null', sample: null, role: 'ignore' });
				}
			}

			// Apply overrides to top fields
			for (const f of topFields) {
				const ps = pathStr(f.path);
				if (fieldRoleOverrides[ps] !== undefined) {
					f.role = fieldRoleOverrides[ps];
				}
			}

			let cycleField = topFields.find((f) => f.role === 'cycle') ?? null;

			if (tabKeys.length === 0) {
				// No nested objects — treat the entire item as a single implicit tab
				const fields = flattenFields(sampleObj);
				for (const f of fields) {
					const ps = pathStr(f.path);
					if (fieldRoleOverrides[ps] !== undefined) {
						f.role = fieldRoleOverrides[ps];
					}
				}
				if (!cycleField) cycleField = fields.find((f) => f.role === 'cycle') ?? null;
				return {
					shape: 'array-of-objects',
					tabs: [{ key: 'default', label: 'Data', yAxisUnit: 'Value', fields }],
					cycleField,
					allFields: fields
				};
			}

			const tabs: TabInfo[] = [];
			const allFields: FieldNode[] = [...topFields];

			for (const tabKey of tabKeys) {
				const nested = sampleObj[tabKey] as Record<string, unknown>;
				const fields = flattenFields(nested);
				// Apply overrides
				for (const f of fields) {
					// Prefix tab key to path for uniqueness
					f.path = [tabKey, ...f.path];
					const ps = pathStr(f.path);
					if (fieldRoleOverrides[ps] !== undefined) {
						f.role = fieldRoleOverrides[ps];
					}
				}

				tabs.push({
					key: tabKey.toLowerCase(),
					label: capitalize(tabKey),
					yAxisUnit: guessYAxisUnit(tabKey),
					fields
				});
				allFields.push(...fields);
			}

			return { shape: 'array-of-objects', tabs, cycleField, allFields };
		}

		return { shape: 'other', tabs: [], cycleField: null, allFields: [], error: 'Expected a top-level object or array' };
