// @source frontend/src/lib/components/PerfGenerator.svelte
// @lines 304-395
// @note generateObjectOfArrays — interface 조립 + tabDefs + chartOption 문자열 템플릿 조립
// @synced 2026-05-01T01:10:31.187Z


	function generateObjectOfArrays(hasDataFields: boolean, cyclePath: string[]): string {
		const sample = mergedTabs[0];
		const cycleAccessor = cyclePath.length > 0 ? cyclePath[cyclePath.length - 1] : 'cycle';

		// Build interface fields from first tab
		const interfaceFields = sample.fields
			.map((f) => {
				const name = f.path[f.path.length - 1];
				if (f.role === 'data') return `\t\t${name}: number[];`;
				if (f.role === 'stat') return `\t\t${name}: number;`;
				if (f.role === 'cycle') return `\t\t${name}: number;`;
				if (f.role === 'ignore' && f.type === 'null') return `\t\t${name}: unknown;`;
				return `\t\t${name}: unknown;`;
			})
			.join('\n');

		const tabDefs = mergedTabs
			.map((t) => `\t\t{ key: '${t.key}', label: '${t.label}' }`)
			.join(',\n');

		const statFields = sample.fields.filter((f) => f.role === 'stat');
		const dataFields = sample.fields.filter((f) => f.role === 'data');
		const dataField = dataFields.length > 0 ? dataFields[0].path[dataFields[0].path.length - 1] : '';
		const hasTabs = mergedTabs.length > 1;
		const yAxisMaxType = hasTabs ? 'record' : (hasDataFields ? 'number' : false);

		const statsColDefs = statFields
			.map((f) => {
				const name = f.path[f.path.length - 1];
				return `\t\t{\n\t\t\taccessorKey: '${name}',\n\t\t\theader: '${name}',\n\t\t\tcell: ({ row }) => row.original.${name}.toFixed(2)\n\t\t}`;
			})
			.join(',\n');

		const chartImport = hasDataFields ? `\n\timport { PerfChart } from '$lib/components/perf-chart';` : '';
		const echartsImport = hasDataFields ? `\n\timport type { EChartsOption } from 'echarts';` : '';
		const perfChartTypeImport = hasDataFields && includeExcelExport
			? `\n\timport type { PerfChart as PerfChartType } from '$lib/components/perf-chart';`
			: '';
		const baseChartOptImport = hasDataFields
			? `\n\timport { baseChartOption } from './perfChartUtils';`
			: '';
		const perfStylesImport = `\n\timport { btnBase, btnActive, btnInactive${hasTabs ? ', btnDisabled' : ''}, groupClass } from './perfStyles';`;
		const emptyStateImport = hasDataFields
			? `\n\timport { emptyState } from '$lib/styles/common.js';`
			: '';
		const downloadIconImport = hasDataFields && includeExcelExport
			? `\n\timport Download from '@lucide/svelte/icons/download';`
			: '';

		const chartStateVars = hasDataFields
			? `\n\t${includeExcelExport ? 'let chartRef: ReturnType<typeof PerfChartType> | undefined = $state();\n\t' : ''}let chartType = $state<'line' | 'scatter'>('line');\n\tlet showRawData = $state(true);`
			: `\n\tlet showRawData = $state(true);`;

		const hasValidDataFn = hasDataFields
			? `\n\n\tfunction hasValidData(cycles: CycleEntry[]): boolean {\n\t\treturn cycles.length > 0 && cycles.some((c) => c.${dataField}?.length > 0);\n\t}`
			: `\n\n\tfunction hasValidData(cycles: CycleEntry[]): boolean {\n\t\treturn cycles.length > 0;\n\t}`;

		const indicesDerived = hasDataFields
			? `\n\n\tconst indices = $derived(() => {\n\t\tconst maxLen = currentCycles.reduce((m, c) => Math.max(m, c.${dataField}?.length ?? 0), 0);\n\t\treturn Array.from({ length: maxLen }, (_, i) => i);\n\t});`
			: '';

		// yAxisMax in chart
		const yAxisMaxLine = yAxisMaxType === 'record'
			? `\n\t\t\t...(yAxisMax?.[activeTab] != null ? { max: yAxisMax[activeTab] } : {})`
			: yAxisMaxType === 'number'
				? `\n\t\t\t...(yAxisMax != null ? { max: yAxisMax } : {})`
				: '';

		const chartOption = hasDataFields
			? `\n\n\tconst chartOption: EChartsOption = $derived({
\t\t...baseChartOption(chartTitle, fw, { left: 90 }),
\t\txAxis: {
\t\t\ttype: 'category',
\t\t\tdata: indices().map(String),
\t\t\tname: '${xAxisUnit}',
\t\t\tnameLocation: 'center',
\t\t\tnameGap: 25
\t\t},
\t\tyAxis: {
\t\t\ttype: 'value',
\t\t\tnameLocation: 'center',
\t\t\tnameRotate: 90,
\t\t\tnameGap: 50,${yAxisMaxLine}
\t\t},
\t\tseries: currentCycles.map((entry) => ({
\t\t\tname: \\\`Cycle \\\${entry.${cycleAccessor}}\\\`,
\t\t\ttype: chartType,
\t\t\tdata: entry.${dataField},
\t\t\tsymbolSize: chartType === 'scatter' ? 4 : undefined,
\t\t\tsmooth: false
\t\t}))
