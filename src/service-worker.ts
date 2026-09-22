/// <reference types="@sveltejs/kit" />
/// <reference lib="webworker" />

// The triple-slash lib reference above scopes this one file to the
// ServiceWorkerGlobalScope types instead of the project's default DOM lib, so
// `self`, `caches`, `skipWaiting` and `clients` are all properly typed here —
// without changing what every other file in the project sees `self` as.

export type {};
declare const self: ServiceWorkerGlobalScope;

// Precaches the app shell — the built JS/CSS and everything in static/, which
// includes the fonts loaded from Google Fonts... no, actually those are
// cross-origin and not part of `files`; see the runtime-cache branch below
// for how they end up available offline too.
//
// Launching from the home screen must work with the network off (§11). The
// draw screen's own data comes from the IndexedDB pool cache (pool.ts), not
// from this worker — this only has to get the shell and the last-seen page
// on screen without a network round trip.

import { build, files, version } from '$service-worker';

const CACHE = `sotao-cards-${version}`;
const PRECACHE: readonly string[] = [...build, ...files];

self.addEventListener('install', (event) => {
	event.waitUntil(
		(async () => {
			const cache = await caches.open(CACHE);
			await cache.addAll(PRECACHE);
			await self.skipWaiting();
		})()
	);
});

self.addEventListener('activate', (event) => {
	event.waitUntil(
		(async () => {
			const keys = await caches.keys();
			await Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)));
			await self.clients.claim();
		})()
	);
});

self.addEventListener('fetch', (event) => {
	if (event.request.method !== 'GET') return;

	const url = new URL(event.request.url);

	// Only this origin. Google Fonts and anything else cross-origin is left to
	// the browser's own HTTP cache — the stylesheet and font files it serves
	// are cacheable and long-lived on their own.
	if (url.origin !== self.location.origin) return;

	event.respondWith(respond(event.request, url));
});

async function respond(request: Request, url: URL): Promise<Response> {
	const cache = await caches.open(CACHE);

	// The built shell: served from cache first, since it is versioned by the
	// build and never needs a network round trip once installed.
	if (PRECACHE.includes(url.pathname)) {
		const cached = await cache.match(url.pathname);
		if (cached) return cached;
	}

	// Everything else — pages, the API — goes to the network first, and what
	// comes back is cached opportunistically so the last few screens the
	// owner actually visited are there if the network drops. This is how a
	// cold, offline launch of /draw ends up with something to render: it was
	// cached the last time the app was opened online.
	try {
		const response = await fetch(request);
		if (response.ok) cache.put(request, response.clone());
		return response;
	} catch (err) {
		const cached = await cache.match(request);
		if (cached) return cached;
		throw err;
	}
}
