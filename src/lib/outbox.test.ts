import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createOutbox, type DrawRecord, type OutboxStore } from './outbox';

// §14's list for outbox.ts. The store is injected, so these run in node with a
// plain Map standing in for IndexedDB — the queueing rules are what matter, not
// the browser API underneath.

function memoryStore(): OutboxStore & { dump: () => DrawRecord[] } {
	let rows: DrawRecord[] = [];
	return {
		async all() {
			return [...rows];
		},
		async put(record) {
			rows = [...rows.filter((r) => r.uuid !== record.uuid), record];
		},
		async remove(uuid) {
			rows = rows.filter((r) => r.uuid !== uuid);
		},
		dump: () => [...rows]
	};
}

function record(uuid: string, cardIds: string[] = ['c1']): DrawRecord {
	return {
		uuid,
		drawnAt: 1_700_000_000_000,
		mode: cardIds.length > 1 ? 'spread' : 'single',
		cards: cardIds.map((id, slot) => ({ slot, slotKind: null, cardId: id }))
	};
}

describe('queueing', () => {
	let store: ReturnType<typeof memoryStore>;

	beforeEach(() => {
		store = memoryStore();
	});

	it('keeps a record that fails to send', async () => {
		const post = vi.fn().mockRejectedValue(new Error('offline'));
		const outbox = createOutbox({ store, post });

		await outbox.enqueue(record('u1'));

		expect(store.dump().map((r) => r.uuid)).toEqual(['u1']);
	});

	it('a queued record survives a reload', async () => {
		// The first outbox queues it while offline and is then thrown away, as a
		// page unload would. A fresh outbox over the same store still has it.
		const offline = createOutbox({ store, post: vi.fn().mockRejectedValue(new Error('offline')) });
		await offline.enqueue(record('u1'));

		const post = vi.fn().mockResolvedValue({ ok: true, status: 200 });
		const reloaded = createOutbox({ store, post });

		expect(await reloaded.pending()).toHaveLength(1);

		await reloaded.flush();

		expect(post).toHaveBeenCalledTimes(1);
		expect(post.mock.calls[0][0].uuid).toBe('u1');
		expect(store.dump()).toHaveLength(0);
	});

	it('clears a record once it is accepted', async () => {
		const post = vi.fn().mockResolvedValue({ ok: true, status: 200 });
		const outbox = createOutbox({ store, post });

		await outbox.enqueue(record('u1'));

		expect(post).toHaveBeenCalledTimes(1);
		expect(store.dump()).toHaveLength(0);
	});

	it('flushes in the order the draws happened', async () => {
		const failing = createOutbox({ store, post: vi.fn().mockRejectedValue(new Error('offline')) });
		await failing.enqueue({ ...record('first'), drawnAt: 1 });
		await failing.enqueue({ ...record('second'), drawnAt: 2 });
		await failing.enqueue({ ...record('third'), drawnAt: 3 });

		const sent: string[] = [];
		const post = vi.fn(async (r: DrawRecord) => {
			sent.push(r.uuid);
			return { ok: true, status: 200 };
		});

		await createOutbox({ store, post }).flush();

		expect(sent).toEqual(['first', 'second', 'third']);
	});
});

describe('idempotence', () => {
	it('sends the same uuid the server already has without changing anything', async () => {
		// The server upserts on uuid, so a replay is accepted and counts once.
		// The outbox's job is simply to send the same uuid, never a fresh one.
		const store = memoryStore();
		const seen = new Map<string, number>();

		const post = vi.fn(async (r: DrawRecord) => {
			seen.set(r.uuid, (seen.get(r.uuid) ?? 0) + 1);
			return { ok: true, status: 200 };
		});

		const outbox = createOutbox({ store, post });
		const r = record('stable-uuid');

		await outbox.enqueue(r);
		await outbox.enqueue(r);

		expect(seen.get('stable-uuid')).toBe(2);
		// Both posts carried the same identity, which is what makes the upsert
		// safe: draw_count is incremented once for this uuid, not twice.
		expect(new Set(post.mock.calls.map((c) => c[0].uuid)).size).toBe(1);
	});

	it('does not queue the same draw twice', async () => {
		const store = memoryStore();
		const outbox = createOutbox({ store, post: vi.fn().mockRejectedValue(new Error('offline')) });

		await outbox.enqueue(record('u1'));
		await outbox.enqueue(record('u1'));

		expect(store.dump()).toHaveLength(1);
	});
});

describe('failure handling', () => {
	it('drops a record the server rejects with a 4xx rather than retrying', async () => {
		// A card was deleted on another device, say. Do not retry forever, and do
		// not surface it.
		const store = memoryStore();
		const post = vi.fn().mockResolvedValue({ ok: false, status: 400 });
		const outbox = createOutbox({ store, post });

		await outbox.enqueue(record('doomed'));

		expect(store.dump()).toHaveLength(0);

		await outbox.flush();
		expect(post).toHaveBeenCalledTimes(1);
	});

	it('keeps a record the server fails on with a 5xx', async () => {
		const store = memoryStore();
		const post = vi.fn().mockResolvedValue({ ok: false, status: 503 });
		const outbox = createOutbox({ store, post });

		await outbox.enqueue(record('later'));

		expect(store.dump().map((r) => r.uuid)).toEqual(['later']);
	});

	it('stops flushing when the network drops, keeping the rest queued', async () => {
		const store = memoryStore();
		const offline = createOutbox({ store, post: vi.fn().mockRejectedValue(new Error('offline')) });
		await offline.enqueue({ ...record('a'), drawnAt: 1 });
		await offline.enqueue({ ...record('b'), drawnAt: 2 });

		const post = vi
			.fn()
			.mockResolvedValueOnce({ ok: true, status: 200 })
			.mockRejectedValueOnce(new Error('offline again'));

		await createOutbox({ store, post }).flush();

		expect(store.dump().map((r) => r.uuid)).toEqual(['b']);
	});

	it('never lets a flush failure throw at the caller', async () => {
		const store = memoryStore();
		const outbox = createOutbox({ store, post: vi.fn().mockRejectedValue(new Error('offline')) });

		await expect(outbox.enqueue(record('u1'))).resolves.not.toThrow();
		await expect(outbox.flush()).resolves.not.toThrow();
	});
});
