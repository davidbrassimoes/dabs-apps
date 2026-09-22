import { OUTBOX_STORE, POOL_STORE, openDb } from './outbox';
import type { Pool } from './draw';

// The cached draw pool (§11).
//
// Fetched on load and stored in IndexedDB under one key. The screen renders
// from the stored copy immediately while the fetch is in flight, so the first
// paint never waits on the network and a launch from the home screen works with
// the network off.

const POOL_KEY = 'current';

/** Refetch when focus returns after more than this (§11). */
export const STALE_AFTER = 5 * 60 * 1000;

function promisify<T>(request: IDBRequest<T>): Promise<T> {
	return new Promise((resolve, reject) => {
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => reject(request.error);
	});
}

export async function readCachedPool(): Promise<Pool | null> {
	try {
		const db = await openDb();
		const tx = db.transaction(POOL_STORE, 'readonly');
		const stored = await promisify(
			tx.objectStore(POOL_STORE).get(POOL_KEY) as IDBRequest<Pool | undefined>
		);
		return stored ?? null;
	} catch {
		// Private windows, blocked storage, a thumbnail capture. Drawing is not
		// possible without a pool, but nothing here should throw at the caller.
		return null;
	}
}

export async function writeCachedPool(pool: Pool): Promise<void> {
	try {
		const db = await openDb();
		const tx = db.transaction(POOL_STORE, 'readwrite');
		await promisify(tx.objectStore(POOL_STORE).put(pool, POOL_KEY));
	} catch {
		// Cache write failed. The in-memory pool still works for this session.
	}
}

export async function fetchPool(fetcher: typeof fetch = fetch): Promise<Pool> {
	const response = await fetcher('/api/pool', { headers: { accept: 'application/json' } });
	if (!response.ok) throw new Error(`Pool fetch failed: ${response.status}`);
	return response.json();
}

/** Fetches and caches. Returns null when the network is not there. */
export async function refreshPool(fetcher: typeof fetch = fetch): Promise<Pool | null> {
	try {
		const pool = await fetchPool(fetcher);
		await writeCachedPool(pool);
		return pool;
	} catch {
		return null;
	}
}

/**
 * Appends drawn ids to the cached recent list straight away, so suppression
 * works across offline draws before the server has heard about any of them.
 */
export function withRecent(pool: Pool, drawnIds: string[]): Pool {
	const recent = [...drawnIds, ...pool.recent.filter((id) => !drawnIds.includes(id))];
	return { ...pool, recent: recent.slice(0, 60) };
}

/** True when the cached copy is old enough to be worth refetching. */
export function isStale(pool: Pool | null, now = Date.now()): boolean {
	return !pool || now - pool.syncedAt > STALE_AFTER;
}

/** Clears both caches. Used when signing out. */
export async function clearCaches(): Promise<void> {
	try {
		const db = await openDb();
		const tx = db.transaction([POOL_STORE, OUTBOX_STORE], 'readwrite');
		await promisify(tx.objectStore(POOL_STORE).clear());
		await promisify(tx.objectStore(OUTBOX_STORE).clear());
	} catch {
		// Nothing to clear, or storage is unavailable.
	}
}
