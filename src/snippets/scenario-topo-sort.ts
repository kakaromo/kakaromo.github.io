// @source frontend/src/routes/agent/scenario-canvas/serializer.ts
// @lines 371-412
// @note topologicalSort — Kahn 알고리즘 + seenEdges 중복 방어 + 정렬 안 된 노드 뒤로
// @synced 2026-04-19T10:15:34.676Z

// ── Internal helpers ──

function topologicalSort(nodes: ScenarioNode[], edges: Edge[]): ScenarioNode[] {
	const nodeMap = new Map(nodes.map(n => [n.id, n]));
	const inDegree = new Map<string, number>();
	const adj = new Map<string, string[]>();

	for (const n of nodes) {
		inDegree.set(n.id, 0);
		adj.set(n.id, []);
	}

	const seenEdges = new Set<string>();
	for (const e of edges) {
		const key = `${e.source}->${e.target}`;
		if (nodeMap.has(e.source) && nodeMap.has(e.target) && !seenEdges.has(key)) {
			seenEdges.add(key);
			adj.get(e.source)!.push(e.target);
			inDegree.set(e.target, (inDegree.get(e.target) ?? 0) + 1);
		}
	}

	const queue = nodes.filter(n => (inDegree.get(n.id) ?? 0) === 0).map(n => n.id);
	const sorted: ScenarioNode[] = [];

	while (queue.length > 0) {
		const id = queue.shift()!;
		sorted.push(nodeMap.get(id)!);
		for (const next of adj.get(id) ?? []) {
			const deg = (inDegree.get(next) ?? 1) - 1;
			inDegree.set(next, deg);
			if (deg === 0) queue.push(next);
		}
	}

	// 정렬 안 된 노드는 끝에 추가
	for (const n of nodes) {
		if (!sorted.includes(n)) sorted.push(n);
	}

	return sorted;
}
