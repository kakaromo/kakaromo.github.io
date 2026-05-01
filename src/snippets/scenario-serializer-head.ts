// @source frontend/src/routes/agent/scenario-canvas/serializer.ts
// @lines 1-70
// @note canvasToProto — Y좌표 정렬 vs 위상 정렬 분기 + hasCondition 판정 + nodeToIndex 매핑
// @synced 2026-05-01T01:05:23.648Z

import type { Edge } from '@xyflow/svelte';
import type { StepForm } from '../AgentStepEditDialog.svelte';
import type { ScenarioNode, StepNodeData, ConditionNodeData } from './types.js';
import { getDefaultParams, getBasicOptions } from '../benchmarkOptions.js';
import { stepSummary } from './types.js';
import type { ScenarioStep, ScenarioLoop } from '$lib/api/agent.js';

export interface StepEdge {
	fromStep: number;
	toStep: number;
	label: string;
}

export interface CanvasProtoResult {
	steps: ScenarioStep[];
	loops: ScenarioLoop[];
	hasBranching: boolean;
	edges: StepEdge[];
}

/**
 * Canvas → Proto format (기존 백엔드 호환)
 * 위상 정렬로 노드를 선형 순서로 변환
 */
export function canvasToProto(
	nodes: ScenarioNode[],
	edges: Edge[],
	loopMembers?: Map<string, Set<string>>
): CanvasProtoResult {
	// 실행 가능한 노드 (step + condition)
	const execNodes = nodes.filter(n => n.type === 'step' || n.type === 'condition');
	if (execNodes.length === 0) return { steps: [], loops: [], hasBranching: false, edges: [] };

	const hasCondition = execNodes.some(n => n.type === 'condition');

	// 분기가 없으면 Y좌표 기준 정렬 (직관적), 분기가 있으면 위상 정렬
	const sorted = hasCondition
		? topologicalSort(execNodes, edges)
		: [...execNodes].sort((a, b) => {
			// 절대 Y좌표로 정렬
			const ay = a.position.y;
			const by = b.position.y;
			return ay - by;
		});

	// nodeId → step index 매핑
	const nodeToIndex = new Map<string, number>();
	sorted.forEach((n, i) => nodeToIndex.set(n.id, i));

	const steps: ScenarioStep[] = sorted.map(node => {
		if (node.type === 'condition') {
			const data = node.data as ConditionNodeData;
			// true/false 분기 대상 찾기
			const trueEdge = edges.find(e => e.source === node.id && e.sourceHandle === 'true');
			const falseEdge = edges.find(e => e.source === node.id && e.sourceHandle === 'false');
			return {
				type: 'condition',
				params: {},
				condition: {
					source: data.source || 'metric',
					metricKey: data.metricKey,
					operator: data.operator,
					threshold: data.threshold,
					thresholdString: data.thresholdString || '',
					shellCommand: data.shellCommand || '',
					extractPattern: data.extractPattern || '',
					rules: data.rules && data.rules.length > 0 ? data.rules : undefined,
					logic: data.logic || 'and',
					trueBranchStep: trueEdge ? (nodeToIndex.get(trueEdge.target) ?? -1) : -1,
					falseBranchStep: falseEdge ? (nodeToIndex.get(falseEdge.target) ?? -1) : -1
