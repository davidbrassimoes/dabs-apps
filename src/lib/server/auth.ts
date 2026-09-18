import { createHmac, timingSafeEqual } from 'node:crypto';

// One password in an env var, one signed cookie, no user table (§10).

export const SESSION_COOKIE = 'sotao_session';

/** One year, in seconds. The cookie always remembers; there is no "remember me". */
export const SESSION_MAX_AGE = 60 * 60 * 24 * 365;

type SessionPayload = { iat: number };

function base64url(buf: Buffer): string {
	return buf.toString('base64url');
}

function sign(payload: string, secret: string): string {
	return base64url(createHmac('sha256', secret).update(payload).digest());
}

/** Compares two strings without leaking their contents through timing. */
export function constantTimeEqual(a: string, b: string): boolean {
	const bufA = Buffer.from(a, 'utf8');
	const bufB = Buffer.from(b, 'utf8');
	// timingSafeEqual throws on a length mismatch, so hash both to a fixed width
	// first. The comparison itself then runs over equal-length buffers.
	const hashA = createHmac('sha256', 'length-equaliser').update(bufA).digest();
	const hashB = createHmac('sha256', 'length-equaliser').update(bufB).digest();
	return timingSafeEqual(hashA, hashB);
}

/** Mints a cookie value: base64url({iat}) . base64url(HMAC-SHA256). */
export function createSessionToken(secret: string, now = Date.now()): string {
	const payload = base64url(Buffer.from(JSON.stringify({ iat: now } satisfies SessionPayload)));
	return `${payload}.${sign(payload, secret)}`;
}

/** True when the token carries an intact signature and is under a year old. */
export function verifySessionToken(token: string, secret: string, now = Date.now()): boolean {
	const dot = token.indexOf('.');
	if (dot < 1) return false;

	const payload = token.slice(0, dot);
	const signature = token.slice(dot + 1);
	if (!constantTimeEqual(signature, sign(payload, secret))) return false;

	let parsed: unknown;
	try {
		parsed = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
	} catch {
		return false;
	}

	if (typeof parsed !== 'object' || parsed === null) return false;
	const { iat } = parsed as Partial<SessionPayload>;
	if (typeof iat !== 'number' || !Number.isFinite(iat)) return false;

	const age = now - iat;
	return age >= 0 && age <= SESSION_MAX_AGE * 1000;
}

// Rate limiting. In memory, per IP: good enough for one user (§10).

const WINDOW_MS = 15 * 60 * 1000;
const MAX_ATTEMPTS = 10;

const attempts = new Map<string, number[]>();

/** True when this IP has already used its 10 failures in the last 15 minutes. */
export function isRateLimited(ip: string, now = Date.now()): boolean {
	const recent = (attempts.get(ip) ?? []).filter((t) => now - t < WINDOW_MS);
	if (recent.length === 0) attempts.delete(ip);
	else attempts.set(ip, recent);
	return recent.length >= MAX_ATTEMPTS;
}

/** Records one failed attempt against this IP. */
export function recordFailedAttempt(ip: string, now = Date.now()): void {
	const recent = (attempts.get(ip) ?? []).filter((t) => now - t < WINDOW_MS);
	recent.push(now);
	attempts.set(ip, recent);
}

/** Clears an IP's failures after a successful login. */
export function clearAttempts(ip: string): void {
	attempts.delete(ip);
}

/** Test seam: drops every recorded attempt. */
export function resetRateLimits(): void {
	attempts.clear();
}
