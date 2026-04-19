// @source frontend/src/lib/components/PerfPreview.svelte
// @lines 1-80
// @note Props + tabData $derived (shape 분기) + cycleAccessor / data/stat 필드 선별
// @synced 2026-04-19T09:49:20.708Z

<script lang="ts">
	import { untrack } from 'svelte';
	import type { AnalysisResult, TabInfo } from './PerfGenerator.types';
	import { PerfChart } from '$lib/components/perf-chart';
	import { DataTable } from '$lib/components/data-table';
	import * as Card from '$lib/components/ui/card';
	import { type ColumnDef } from '@tanstack/table-core';
	import type { EChartsOption } from 'echarts';
	import { baseChartOption } from '$lib/components/perf-content/perfChartUtils';
	import { btnBase, btnActive, btnInactive, groupClass } from '$lib/components/perf-content/perfStyles';

	interface Props {
		analysis: AnalysisResult;
		mergedTabs: TabInfo[];
		parsedData: unknown;
		xAxisUnit: string;
		componentName: string;
	}

	let { analysis, mergedTabs, parsedData, xAxisUnit, componentName }: Props = $props();

	let activeTab = $state('');
	let chartType = $state<'line' | 'scatter'>('line');

	// Reset active tab when tabs change
	$effect(() => {
		const tabs = mergedTabs;
		untrack(() => {
			if (tabs.length > 0 && !tabs.some((t) => t.key === activeTab)) {
				activeTab = tabs[0].key;
			}
		});
	});

	const currentTab = $derived(mergedTabs.find((t) => t.key === activeTab));

	// Extract data for current tab
	const tabData = $derived.by(() => {
		if (!parsedData || !currentTab) return { cycles: [] as Record<string, unknown>[] };

		if (analysis.shape === 'object-of-arrays') {
			const obj = parsedData as Record<string, unknown>;
			// Find the matching key (case-insensitive)
			const matchKey = Object.keys(obj).find((k) => k.toLowerCase() === activeTab);
			if (matchKey && Array.isArray(obj[matchKey])) {
				return { cycles: obj[matchKey] as Record<string, unknown>[] };
			}
			return { cycles: [] as Record<string, unknown>[] };
		}

		if (analysis.shape === 'array-of-objects') {
			const arr = parsedData as Record<string, unknown>[];
			return { cycles: arr };
		}

		return { cycles: [] as Record<string, unknown>[] };
	});

	// Find cycle field accessor
	const cycleAccessor = $derived(
		analysis.cycleField ? analysis.cycleField.path[analysis.cycleField.path.length - 1] : null
	);

	// Get data fields and stat fields for current tab
	const dataFields = $derived(currentTab?.fields.filter((f) => f.role === 'data') ?? []);
	const statFields = $derived(currentTab?.fields.filter((f) => f.role === 'stat') ?? []);
	const hasDataFields = $derived(dataFields.length > 0);

	// For array-of-objects, need to access nested tab data
	function getNestedValue(obj: Record<string, unknown>, path: string[]): unknown {
		let current: unknown = obj;
		for (const key of path) {
			if (current === null || current === undefined || typeof current !== 'object') return undefined;
			current = (current as Record<string, unknown>)[key];
		}
		return current;
	}

	// Build chart series data
	const chartSeriesData = $derived.by(() => {
