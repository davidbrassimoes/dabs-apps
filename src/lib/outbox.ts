import type { DrawMode, Kind } from './vocab';

// Queued draw records (§11).
//
// The client performs the draw and then posts the record. That post may be
// queued offline and replayed later, so the client mints the draw's identity
// and the endpoint upserts on it — replaying the same record twice must not
// double-count draw_count.
//
// The store is injected rather than imported, so this module is testable in
// node and the IndexedDB details live in one adapter at the bottom.

export type DrawRecordCard = {
	slot: number;
	slotKind: Kind | null;
	cardId: string;
};

export type DrawRecord = {
	/** Minted by the client at draw time. The server upserts on this (§6). */
	uuid: string;
	drawnAt: number;
	mode: DrawMode;
	cards: DrawRecordCard[];
};

export type OutboxStore = {
	all(): Promise<DrawRecord[]>;
	put(record: DrawRecord): Promise<void>;
	remove(uuid: string): Promise<void>;
};

export type PostResult = { ok: boolean; status: number };

export type Outbox = {
	/** Queues a draw and tries to send it. Never throws. */
	enqueue(record: DrawRecord): Promise<void>;
	/** Sends everything queued, oldest first. Never throws. */
	flush(): Promise<void>;
	pending(): Promise<DrawRecord[]>;
};

export function createOutbox(deps: {
	store: OutboxStore;
	post: (record: DrawRecord) => Promise<PostResult>;
}): Outbox {
	const { store, post } = deps;

	/**
	 * True when the record is done with — either the server took it, or it
	 * rejected it with a 4xx and retrying would never help.
	 *
	 * A 4xx means something about the record itself is wrong: a card deleted on
	 * another device, say. Drop it and move on. Do not retry forever, and do not
	 * surface it (§11).
	 */
	async function send(record: DrawRecord): Promise<boolean> {
		const result = await post(record);
		if (result.ok) return true;
		return result.status >= 400 && result.status < 500;
	}

	async function flush(): Promise<void> {
		const queued = (await store.all()).sort((a, b) => a.drawnAt - b.drawnAt);

		for (const record of queued) {
			try {
				if (await send(record)) await store.remove(record.uuid);
				else return; // Server trouble. Keep this one and the rest for later.
			} catch {
				// Offline, most likely. A flush failure is not an error the user
				// sees; it retries on the next draw, on reconnect, or on load.
				return;
			}
		}
	}

	return {
		async enqueue(record) {
			// Written first, so the draw survives a reload even if the post is
			// still in flight when the tab goes away.
			await store.put(record);

			try {
				if (await send(record)) await store.remove(record.uuid);
			} catch {
				// Stays queued.
			}
		},

		flush,

		pending() {
			return store.all();
		}
	};
}

// The IndexedDB adapter. One object store keyed by uuid.

const DB_NAME = 'sotao-cards';
const DB_VERSION = 1;
export const OUTBOX_STORE = 'outbox';
export const POOL_STORE = 'pool';

export function openDb(): Promise<IDBDatabase> {
	return new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, DB_VERSION);
		request.onupgradeneeded = () => {
			const db = request.result;
			if (!db.objectStoreNames.contains(OUTBOX_STORE)) {
				db.createObjectStore(OUTBOX_STORE, { keyPath: 'uuid' });
			}
			if (!db.objectStoreNames.contains(POOL_STORE)) {
				db.createObjectStore(POOL_STORE);
			}
		};
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => reject(request.error);
	});
}

function promisify<T>(request: IDBRequest<T>): Promise<T> {
	return new Promise((resolve, reject) => {
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => reject(request.error);
	});
}

export function indexedDbStore(): OutboxStore {
	return {
		async all() {
			const db = await openDb();
			const tx = db.transaction(OUTBOX_STORE, 'readonly');
			return promisify(tx.objectStore(OUTBOX_STORE).getAll() as IDBRequest<DrawRecord[]>);
		},
		async put(record) {
			const db = await openDb();
			const tx = db.transaction(OUTBOX_STORE, 'readwrite');
			await promisify(tx.objectStore(OUTBOX_STORE).put(record));
		},
		async remove(uuid) {
			const db = await openDb();
			const tx = db.transaction(OUTBOX_STORE, 'readwrite');
			await promisify(tx.objectStore(OUTBOX_STORE).delete(uuid));
		}
	};
}
