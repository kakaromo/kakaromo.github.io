// @source frontend/src/lib/api/headSlotStore.svelte.ts
// @lines 55-177
// @note createHeadSlotStore — $state + EventSource + dedup + retry
// @synced 2026-04-19T05:26:12.212Z

interface HeadSsePayload {
	slots: HeadSlotData[];
	version: number;
	connections: ConnectionStatus[];
}

export function createHeadSlotStore(source?: string) {
	let slots = $state<HeadSlotData[]>([]);
	let connections = $state<ConnectionStatus[]>([]);
	let connected = $state(false);
	let connecting = $state(false);
	let version = $state(0);
	let retryCount = $state(0);
	let lastError = $state<string | null>(null);
	let eventSource: EventSource | null = null;

	/** Deduplicate slots by slotIndex (last wins) */
	function dedup(raw: HeadSlotData[]): HeadSlotData[] {
		const map = new Map<number, HeadSlotData>();
		for (const s of raw) map.set(s.slotIndex, s);
		return [...map.values()];
	}

	function connect() {
		if (eventSource) {
			eventSource.close();
		}

		connecting = true;
		lastError = null;

		const params = source ? `?source=${source}` : '';
		eventSource = new EventSource(`/api/head/slots/stream${params}`);

		eventSource.addEventListener('init', (e: MessageEvent) => {
			const payload: HeadSsePayload = JSON.parse(e.data);
			slots = dedup(payload.slots);
			version = payload.version;
			connections = payload.connections;
			connected = true;
			connecting = false;
			retryCount = 0;
			lastError = null;
		});

		eventSource.addEventListener('update', (e: MessageEvent) => {
			const payload: HeadSsePayload = JSON.parse(e.data);
			slots = dedup(payload.slots);
			version = payload.version;
			connections = payload.connections;
		});

		eventSource.onopen = () => {
			connected = true;
			connecting = false;
			retryCount = 0;
			lastError = null;
		};

		eventSource.onerror = () => {
			connected = false;
			connecting = false;
			lastError = 'Connection failed';
			// EventSource auto-reconnects by default
		};
	}

	function disconnect() {
		if (eventSource) {
			eventSource.close();
			eventSource = null;
		}
		connected = false;
		connecting = false;
	}

	async function retry() {
		retryCount++;
		connecting = true;
		lastError = null;

		// 1. Request backend to reconnect to Head server
		if (source) {
			try {
				await reconnectHead(source);
			} catch (e) {
				// Backend reconnect failed, but still try SSE reconnect
				console.warn(`Backend reconnect failed for ${source}:`, e);
			}
		}

		// 2. Reconnect SSE stream
		disconnect();
		connect();
	}

	return {
		get slots() {
			return slots;
		},
		get connections() {
			return connections;
		},
		get connected() {
			return connected;
		},
		get connecting() {
			return connecting;
		},
		get version() {
			return version;
		},
		get retryCount() {
			return retryCount;
		},
		get lastError() {
			return lastError;
		},
		connect,
		disconnect,
		retry
	};
}
