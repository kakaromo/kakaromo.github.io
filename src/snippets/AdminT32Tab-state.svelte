// @source frontend/src/routes/admin/AdminT32Tab.svelte
// @lines 25-67
// @note state · derived(groupServers) · loadAll(Promise.all) — 3 fetch 병렬
// @synced 2026-05-01T01:05:23.653Z

	// ── State ──
	let configs = $state<T32Config[]>([]);
	let servers = $state<PortalServer[]>([]);
	let serverGroups = $state<ServerGroup[]>([]);
	let loading = $state(false);
	let saving = $state(false);

	// Dialog
	let dialogOpen = $state(false);
	let dialogMode = $state<'create' | 'edit'>('create');
	let editingId = $state<number | null>(null);
	let form = $state<Partial<T32ConfigCreateRequest>>({});
	let selectedGroupId = $state<number | null>(null);

	// Confirm
	let confirmOpen = $state(false);
	let confirmDeleteId = $state<number>(0);
	let confirmDeleteName = $state('');

	// ── Derived: 선택한 서버 그룹의 서버 목록 ──
	let groupServers = $derived(
		selectedGroupId
			? servers.filter((s) => s.serverGroupId === selectedGroupId)
			: []
	);

	// ── Load ──
	async function loadAll() {
		loading = true;
		try {
			[configs, servers, serverGroups] = await Promise.all([
				fetchConfigs(),
				fetchServers(),
				fetchServerGroups()
			]);
		} catch {
			toast.error('데이터 로드 실패');
		} finally {
			loading = false;
		}
	}

	loadAll();
