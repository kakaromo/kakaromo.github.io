// @source frontend/src/routes/agent/scenario-canvas/types.ts
// @lines 1-84
// @note StepNodeData/ConditionNodeData/LoopGroupData + ScenarioNode 유니온 + STEP_TYPE_COLORS 8종 + stepSummary
// @synced 2026-05-01T01:10:31.190Z

import type { Node, Edge } from '@xyflow/svelte';
import type { StepForm } from '../AgentStepEditDialog.svelte';
import type { ThreadProgress } from '../iotest/types.js';

export interface StepNodeData {
	stepForm: StepForm;
	label: string;
	stepType: string;
	execOrder?: number;  // 실행 순서 (1-based)
	execStatus?: 'idle' | 'running' | 'completed' | 'failed' | 'skipped';
	execLoopCurrent?: number;
	execLoopTotal?: number;
	execProgress?: number;
	// iotest stepType 일 때 thread별 진행률 — Go agent 의 stderr JSONL → SSE 로 forward 되는
	// 데이터를 ScenarioCanvas 가 채워준다. 데이터가 없으면 노드는 기존대로 렌더.
	threadProgresses?: ThreadProgress[];
}

export interface ConditionRule {
	source: string;           // "metric" | "shell"
	metricKey: string;
	operator: string;
	threshold: number;
	thresholdString: string;
	shellCommand: string;
	extractPattern: string;
}

export interface ConditionNodeData {
	source: string;           // "metric" | "shell" (단일 조건용, 하위 호환)
	metricKey: string;
	operator: string;
	threshold: number;
	thresholdString: string;
	shellCommand: string;
	extractPattern: string;
	rules: ConditionRule[];   // 복합 조건
	logic: string;            // "and" | "or"
	execOrder?: number;
	execStatus?: 'idle' | 'running' | 'completed' | 'failed' | 'skipped';
}

export interface LoopGroupData {
	loopCount: number;
	label: string;
}

export type ScenarioNode =
	| Node<StepNodeData, 'step'>
	| Node<ConditionNodeData, 'condition'>
	| Node<LoopGroupData, 'loopGroup'>;

export type ScenarioEdge = Edge;

export interface NodeExecutionState {
	status: 'idle' | 'running' | 'completed' | 'failed' | 'skipped';
	loopCurrent?: number;
	loopTotal?: number;
	repeatCurrent?: number;
	repeatTotal?: number;
	progressPercent?: number;
	error?: string;
}

export const STEP_TYPE_COLORS: Record<string, { bg: string; text: string }> = {
	benchmark: { bg: 'bg-blue-100', text: 'text-blue-700' },
	iotest: { bg: 'bg-cyan-100', text: 'text-cyan-700' },
	shell: { bg: 'bg-gray-100', text: 'text-gray-700' },
	cleanup: { bg: 'bg-orange-100', text: 'text-orange-700' },
	sleep: { bg: 'bg-yellow-100', text: 'text-yellow-700' },
	trace_start: { bg: 'bg-emerald-100', text: 'text-emerald-700' },
	trace_stop: { bg: 'bg-emerald-100', text: 'text-emerald-700' },
	app_macro: { bg: 'bg-violet-100', text: 'text-violet-700' }
};

export function stepSummary(form: StepForm): string {
	switch (form.type) {
		case 'benchmark': return `${form.tool} · ${form.formParams.rw ?? ''} · ${form.formParams.bs ?? ''}`;
		case 'iotest': return `${form.iotestConfig?.threads.length ?? 0} threads`;
		case 'shell': return form.extraText.slice(0, 30) || 'shell';
		case 'cleanup': return form.cleanupMode === 'all' ? '전체 삭제' : form.cleanupMode === 'steps' ? 'step 파일 삭제' : form.cleanupPath || '삭제';
		case 'sleep': return `${form.extraText.replace('seconds=', '')}s`;
		case 'trace_start': return `${form.formParams.trace_type ?? 'ufs'} trace`;
		case 'trace_stop': return 'stop';
